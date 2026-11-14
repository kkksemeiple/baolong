package com.baolong.wsw.service;

import com.baolong.wsw.common.BizException;
import com.baolong.wsw.common.ResultCode;
import com.baolong.wsw.entity.BargainRecord;
import com.baolong.wsw.entity.Post;
import com.baolong.wsw.entity.Report;
import com.baolong.wsw.entity.Task;
import com.baolong.wsw.entity.AuditItem;
import com.baolong.wsw.enums.BargainStatus;
import com.baolong.wsw.enums.ReportType;
import com.baolong.wsw.enums.TaskStatus;
import com.baolong.wsw.mapper.AuditItemMapper;
import com.baolong.wsw.mapper.BargainRecordMapper;
import com.baolong.wsw.mapper.PostMapper;
import com.baolong.wsw.mapper.ReportMapper;
import com.baolong.wsw.mapper.TaskMapper;
import com.baolong.wsw.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import com.baolong.wsw.dto.InteractionDto;
import java.util.ArrayList;
import java.util.List;

/**
 * 议价与举报 —— 两类「不改变主流程状态机」的交互，单独放一个服务。
 *
 * <p>议价对应设计文档的 M12 isEligible / M04 acceptAfterBargain：
 * 报价区间为 {@code [0, reward × 1.5]}，允许 0 元报价（帮忙性质），但不得高于报酬的 1.5 倍；
 * 同一任务同一时刻至多一条 PENDING 报价。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionService {

    private static final double BARGAIN_CEIL_RATIO = 1.5;

    private final TaskMapper taskMapper;
    private final PostMapper postMapper;
    private final BargainRecordMapper bargainRecordMapper;
    private final ReportMapper reportMapper;
    private final AuditItemMapper auditItemMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ 议价

    @Transactional(rollbackFor = Exception.class)
    public BargainRecord offer(Long taskId, Long fromUserId, long amount, String message) {
        Task task = requireTask(taskId);
        if (task.getPublisherId().equals(fromUserId)) {
            throw BizException.badRequest("不能给自己发布的任任务报价");
        }
        if (!TaskStatus.WAITING.name().equals(task.getStatus())) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID, "任务已不在待接单状态");
        }
        long ceil = (long) Math.floor(task.getReward() * BARGAIN_CEIL_RATIO);
        if (amount < 0 || amount > ceil) {
            throw BizException.of(ResultCode.BARGAIN_NOT_ALLOWED,
                    "报价需在 0 ~ " + ceil + " 屋币之间");
        }

        // 同一任务同一时刻至多一条 PENDING
        bargainRecordMapper.invalidatePending(taskId);
        BargainRecord record = new BargainRecord();
        record.setTaskId(taskId);
        record.setFromUserId(fromUserId);
        record.setToUserId(task.getPublisherId());
        record.setAmount(amount);
        record.setStatus(BargainStatus.PENDING.name());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        bargainRecordMapper.insert(record);

        notificationService.pushTask(task.getPublisherId(), "收到新的报价",
                "任务「" + task.getTitle() + "」有人报价 " + amount + " 屋币", taskId);
        return record;
    }

    /** 发布人回应报价：同意则把任务报酬改成成交价 */
    @Transactional(rollbackFor = Exception.class)
    public void respond(Long taskId, Long publisherId, Long bargainId, boolean accept, String remark) {
        Task task = requireTask(taskId);
        if (!task.getPublisherId().equals(publisherId)) {
            throw BizException.forbidden("只有发布人能处理报价");
        }
        if (!TaskStatus.WAITING.name().equals(task.getStatus())) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID, "任务已不在待接单状态");
        }
        BargainRecord record = bargainRecordMapper.selectById(bargainId);
        if (record == null || !record.getTaskId().equals(taskId)) {
            throw BizException.notFound("报价不存在");
        }
        if (!BargainStatus.PENDING.name().equals(record.getStatus())) {
            throw BizException.conflict("该报价已处理");
        }

        record.setStatus(accept ? BargainStatus.ACCEPTED.name() : BargainStatus.REJECTED.name());
        record.setUpdatedAt(LocalDateTime.now());
        bargainRecordMapper.updateById(record);

        if (accept) {
            // 成交价替换任务报酬：先释放旧冻结，再按新价冻结
            long delta = record.getAmount() - task.getReward();
            task.setReward(record.getAmount());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            if (delta > 0) {
                log.info("议价成交价高于原报酬，差额 {} 需发布人补足", delta);
            }
            notificationService.pushTask(record.getFromUserId(), "报价已被接受",
                    "任务「" + task.getTitle() + "」以 " + record.getAmount() + " 屋币成交", taskId);
        } else {
            notificationService.pushTask(record.getFromUserId(), "报价被拒绝",
                    "任务「" + task.getTitle() + "」的报价未被接受", taskId);
        }
    }

    public List<InteractionDto.BargainVO> list(Long taskId, Long currentUserId) {
        Task task = requireTask(taskId);
        boolean related = task.getPublisherId().equals(currentUserId)
                || (task.getClaimantId() != null && task.getClaimantId().equals(currentUserId));
        if (!related) {
            throw BizException.forbidden("只有任务相关方才能查看报价");
        }
        List<BargainRecord> records = bargainRecordMapper.selectList(
                Wrappers.<BargainRecord>lambdaQuery()
                        .eq(BargainRecord::getTaskId, taskId)
                        .orderByDesc(BargainRecord::getId));
        List<InteractionDto.BargainVO> result = new ArrayList<>(records.size());
        for (BargainRecord record : records) {
            String nickname = null;
            if (record.getFromUserId() != null) {
                try {
                    var user = userMapper.selectById(record.getFromUserId());
                    if (user != null) {
                        nickname = user.getNickname() == null ? user.getUsername() : user.getNickname();
                    }
                } catch (Exception ignored) {
                    // 用户被删也能看报价金额，不影响主流程
                }
            }
            BargainStatus status = null;
            try {
                status = BargainStatus.valueOf(record.getStatus());
            } catch (Exception ignored) {
                // 历史脏数据不认识的取值，原样返回
            }
            result.add(new InteractionDto.BargainVO(
                    record.getId(),
                    record.getTaskId(),
                    record.getFromUserId(),
                    nickname,
                    record.getToUserId(),
                    record.getAmount() == null ? null : record.getAmount().intValue(),
                    record.getStatus(),
                    status == null ? record.getStatus() : status.label(),
                    record.getCreatedAt()));
        }
        return result;
    }

    // ------------------------------------------------------------------ 举报

    @Transactional(rollbackFor = Exception.class)
    public void reportTask(Long taskId, Long reporterId, String type, String reason, String evidence) {
        Task task = requireTask(taskId);
        if (task.getPublisherId().equals(reporterId)) {
            throw BizException.badRequest("不能举报自己的任务");
        }
        submitReport(taskId, null, task.getTitle(), task.getPublisherId(),
                reporterId, type, reason, evidence);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reportPost(Long postId, Long reporterId, String type, String reason, String evidence) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw BizException.notFound("帖子不存在");
        }
        if (post.getAuthorId().equals(reporterId)) {
            throw BizException.badRequest("不能举报自己的帖子");
        }
        submitReport(null, postId, post.getTitle(), post.getAuthorId(),
                reporterId, type, reason, evidence);
    }

    // ------------------------------------------------------------------ 内部

    private void submitReport(Long taskId, Long postId, String targetTitle, Long authorId,
                              Long reporterId, String type, String reason, String evidence) {
        String reportType = type == null ? ReportType.OTHER.name() : type.toUpperCase();
        Report report = new Report();
        report.setType(reportType);
        report.setTaskId(taskId);
        report.setPostId(postId);
        report.setTargetTitle(targetTitle);
        report.setReporterId(reporterId);
        report.setReason(reason);
        report.setEvidence(evidence);
        report.setStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);

        // 举报不能「石沉大海」：被举报的人要立刻知道自己被举报了（但先不判定对错），
        // 举报人也要收到「已受理」，否则双方都不知道这件事有没有被看见。
        String what = taskId != null ? "任务" : "帖子";
        String link = taskId != null ? "/tasks/" + taskId : "/forum/" + postId;
        notificationService.push(authorId, com.baolong.wsw.enums.NotifType.SYSTEM,
                "你的" + what + "被举报了",
                "「" + targetTitle + "」收到一条举报，平台正在核实，请留意后续处理结果。",
                taskId != null ? taskId : postId, link);
        notificationService.push(reporterId, com.baolong.wsw.enums.NotifType.SYSTEM,
                "举报已受理",
                "你对" + what + "「" + targetTitle + "」的举报已提交，管理员会尽快核实。",
                taskId != null ? taskId : postId, link);

        // 注意：这里**不再**往 audit_item 插一条。
        // 举报和「敏感词待审」是两个独立队列：举报只在「举报」页处理，
        // 敏感词只在「待审内容」页处理。以前互相写会让同一内容在两页各出现一次，
        // 处理完一边、另一边还挂着，状态永远对不齐。
    }

    private Task requireTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        return task;
    }
}
