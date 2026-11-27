package com.baolong.wsw.schedule;

import com.baolong.wsw.entity.Task;
import com.baolong.wsw.entity.TaskClaim;
import com.baolong.wsw.enums.BizType;
import com.baolong.wsw.enums.ClaimStatus;
import com.baolong.wsw.enums.TaskStatus;
import com.baolong.wsw.mapper.TaskClaimMapper;
import com.baolong.wsw.mapper.TaskMapper;
import com.baolong.wsw.service.ConfigService;
import com.baolong.wsw.service.CreditService;
import com.baolong.wsw.service.NotificationService;
import com.baolong.wsw.service.WalletService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务养护作业：到期未接自动过期、待确认超时自动结算、接单超时关闭。
 *
 * <p>这是「Redis 延迟队列」的兜底实现 —— 课程项目不引入消息中间件，
 * 用固定频率轮询保证即使服务重启也不会漏掉过期与自动确认；
 * 文档里会说明为什么选轮询而不是 ZSet 延迟队列（可观测、无额外组件、量级足够）。
 *
 * <p><b>事务边界</b>：每条任务的操作（状态流转 + 加钱/减钱 + 通知）必须是一个整体。
 * 以前这里没有事务，中途抛异常就会留下「钱退了但任务还在大厅，能被再抢一次」
 * 或「任务已结算但接单方没收到钱」这类半截账。所以每条都包进
 * {@link TransactionTemplate#executeWithoutResult}，单条失败回滚、不影响整批。
 *
 * <p><b>命名注意</b>：类名不能叫 {@code TaskScheduler}。Spring Boot 的
 * {@code TaskSchedulingAutoConfiguration} 已经注册了一个名叫 {@code taskScheduler}
 * 的 {@link org.springframework.scheduling.TaskScheduler} 线程池，同名的
 * {@code @Component} 会与之冲突，启动时报
 * 「The bean 'taskScheduler' ... could not be registered」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMaintenanceJob {

    private static final int BATCH = 200;

    private final TaskMapper taskMapper;
    private final TaskClaimMapper taskClaimMapper;
    private final WalletService walletService;
    private final CreditService creditService;
    private final ConfigService configService;
    private final NotificationService notificationService;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 每 60 秒扫一次（原来 10 分钟）。
     * <p>用户实测「任务已过截止时间、大厅里还挂着」：状态要等下一轮才改。
     * 现在查询层已即时过滤（{@code deadline > now}），这里再缩到 1 分钟，
     * 保证过期后报酬尽快退回，而不只是列表里看不见。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void run() {
        try {
            int expired = expireOverdue();
            int confirmed = autoConfirm();
            int timedOut = timeoutInProgress();
            purgeIdemRecords();
            if (expired > 0 || confirmed > 0 || timedOut > 0) {
                log.info("定时任务完成：过期 {} 条，自动确认 {} 条，超时关闭 {} 条", expired, confirmed, timedOut);
            }
        } catch (Exception e) {
            log.error("定时任务执行失败", e);
        }
    }

    /**
     * 清理过期幂等记录。
     * <p>{@code idem_record} 每次写请求都会插一行，只增不减会一直涨；
     * 保留 2 天足够覆盖「用户手抖连点 / 网络重试」的场景。
     */
    private void purgeIdemRecords() {
        try {
            int removed = jdbcTemplate.update(
                    "DELETE FROM idem_record WHERE created_at < NOW() - INTERVAL 2 DAY");
            if (removed > 0) {
                log.info("清理幂等记录 {} 条", removed);
            }
        } catch (Exception e) {
            log.warn("清理幂等记录失败: {}", e.getMessage());
        }
    }

    /** 截止时间已过且还没人接的任务：解冻报酬并置为 EXPIRED */
    private int expireOverdue() {
        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getStatus, TaskStatus.WAITING.name())
                .lt(Task::getDeadline, LocalDateTime.now())
                .last("LIMIT " + BATCH));
        int count = 0;
        for (Task task : tasks) {
            try {
                transactionTemplate.executeWithoutResult(status -> expireOne(task));
                count++;
            } catch (Exception e) {
                log.warn("任务过期处理失败: taskId={}", task.getId(), e);
            }
        }
        return count;
    }

    private void expireOne(Task task) {
        walletService.unfreeze(task.getPublisherId(), task.getReward(), BizType.TASK,
                task.getId(), "任务过期退回：" + task.getTitle(), "TASK_EXPIRE", task.getId());
        if (taskMapper.expire(task.getId()) == 0) {
            // 并发下可能已被别的线程处理，抛异常让事务回滚刚做的解冻
            throw new IllegalStateException("任务状态已变化，跳过过期处理: " + task.getId());
        }
        notificationService.pushTask(task.getPublisherId(), "任务已过期",
                "「" + task.getTitle() + "」到截止时间仍无人接单，报酬已退回钱包", task.getId());
    }

    /** 接单方提交后超过 autoConfirmHours 小时发布人未确认：自动结算 */
    private int autoConfirm() {
        int hours = configService.getInt("autoConfirmHours", 72);
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        List<TaskClaim> claims = taskClaimMapper.selectList(Wrappers.<TaskClaim>lambdaQuery()
                .eq(TaskClaim::getStatus, ClaimStatus.SUBMITTED.name())
                .lt(TaskClaim::getSubmittedAt, threshold)
                .last("LIMIT " + BATCH));
        int count = 0;
        for (TaskClaim claim : claims) {
            try {
                Boolean done = transactionTemplate.execute(status -> autoConfirmOne(claim, hours));
                if (Boolean.TRUE.equals(done)) {
                    count++;
                }
            } catch (Exception e) {
                log.warn("自动确认失败: claimId={}", claim.getId(), e);
            }
        }
        return count;
    }

    private boolean autoConfirmOne(TaskClaim claim, int hours) {
        Task task = taskMapper.selectById(claim.getTaskId());
        if (task == null || !TaskStatus.TO_CONFIRM.name().equals(task.getStatus())) {
            return false;
        }
        // 必须先把任务从 TO_CONFIRM 推到 COMPLETED 并检查影响行数。
        // 原来的写法是直接 settle，而 settle 的守卫是 status='COMPLETED'，
        // 待确认状态根本更新不到 —— 结果钱划走了、任务却永远停在「待确认」，
        // 发布人之后还能手动点确认，再赔一次（自动结算与手动结算用的是两套幂等键，防不住）。
        if (taskMapper.confirm(task.getId(), task.getPublisherId()) == 0) {
            return false;
        }
        if (taskMapper.settle(task.getId()) == 0) {
            throw new IllegalStateException("自动结算未能置为已结算: " + task.getId());
        }
        walletService.consumeFrozen(task.getPublisherId(), task.getReward());
        walletService.income(claim.getClaimerId(), task.getReward(), BizType.TASK,
                task.getId(), "任务自动结算：" + task.getTitle(), "AUTO_CONFIRM", claim.getId());
        taskClaimMapper.confirm(task.getId(), null, "超时自动确认");
        creditService.refresh(claim.getClaimerId());
        creditService.refresh(task.getPublisherId());
        notificationService.pushTask(claim.getClaimerId(), "任务已自动结算",
                "「" + task.getTitle() + "」超过 " + hours + " 小时未确认，系统已自动结算", task.getId());
        return true;
    }

    /**
     * 接单方超时未交付：任务已过截止时间、状态仍是「进行中」。
     *
     * <p>之前只有「待接取过期」和「待确认自动结算」两条兜底，
     * 缺了这条：接单方接了不做、发布人也不主动取消时，
     * 任务会永远停在进行中，报酬一直冻结在发布人账上，
     * 双方的钱和任务都卡死（实测中已出现）。
     *
     * <p>宽限期取 {@code claimTimeoutHours}（默认 2 小时，可在后台改）。
     * 留缓冲是为了别让「卡在最后一分钟提交」被判死刑，但也不能拖太久 ——
     * 拖一天的话，发布人看着一条「已截止但还在进行中」的任务无从处理。
     * 超时后：关闭任务 → 报酬退回发布人 → 接单记录按「接单方责任」作废（计入取消率）→ 通知双方。
     */
    private int timeoutInProgress() {
        int hours = configService.getInt("claimTimeoutHours", 2);
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getStatus, TaskStatus.IN_PROGRESS.name())
                .lt(Task::getDeadline, threshold)
                .last("LIMIT " + BATCH));
        int count = 0;
        for (Task task : tasks) {
            try {
                transactionTemplate.executeWithoutResult(status -> timeoutOne(task, hours));
                count++;
            } catch (Exception e) {
                log.warn("任务超时关闭失败: taskId={}", task.getId(), e);
            }
        }
        return count;
    }

    private void timeoutOne(Task task, int hours) {
        Long claimantId = task.getClaimantId();
        String reason = "超过截止时间 " + hours + " 小时仍未提交，系统自动关闭";
        if (taskMapper.timeoutClose(task.getId(), reason) == 0) {
            throw new IllegalStateException("任务状态已变化，跳过超时关闭: " + task.getId());
        }
        taskClaimMapper.cancel(task.getId(), reason, "CLAIMER");
        walletService.unfreeze(task.getPublisherId(), task.getReward(), BizType.TASK,
                task.getId(), "任务超时关闭退回：" + task.getTitle(), "TASK_TIMEOUT", task.getId());
        if (task.getPublisherId() != null) {
            creditService.refresh(task.getPublisherId());
        }
        if (claimantId != null) {
            creditService.refresh(claimantId);
        }
        notificationService.pushTask(task.getPublisherId(), "任务已超时关闭",
                "「" + task.getTitle() + "」接单方超过截止时间 " + hours + " 小时未提交，任务已关闭，报酬已退回钱包",
                task.getId());
        if (claimantId != null) {
            notificationService.pushTask(claimantId, "任务超时被关闭",
                    "「" + task.getTitle() + "」你未在截止时间后 " + hours + " 小时内提交，任务已被系统关闭，本次计入放弃",
                    task.getId());
        }
    }
}