package com.baolong.wsw.service;

import com.baolong.wsw.common.BizException;
import com.baolong.wsw.common.PageResult;
import com.baolong.wsw.common.ResultCode;
import com.baolong.wsw.dto.AdminDto;
import com.baolong.wsw.dto.TaskDto;
import com.baolong.wsw.entity.AdminOpLog;
import com.baolong.wsw.entity.AuditItem;
import com.baolong.wsw.entity.Dispute;
import com.baolong.wsw.entity.Post;
import com.baolong.wsw.entity.Report;
import com.baolong.wsw.entity.Task;
import com.baolong.wsw.entity.User;
import com.baolong.wsw.entity.UserCredit;
import com.baolong.wsw.entity.WalletAccount;
import com.baolong.wsw.entity.WalletTransaction;
import com.baolong.wsw.enums.AuthStatus;
import com.baolong.wsw.enums.TaskStatus;
import com.baolong.wsw.enums.TransType;
import com.baolong.wsw.enums.UserStatus;
import com.baolong.wsw.mapper.AdminOpLogMapper;
import com.baolong.wsw.mapper.AuditItemMapper;
import com.baolong.wsw.mapper.DisputeMapper;
import com.baolong.wsw.mapper.PostMapper;
import com.baolong.wsw.mapper.ReportMapper;
import com.baolong.wsw.mapper.TaskClaimMapper;
import com.baolong.wsw.mapper.TaskMapper;
import com.baolong.wsw.mapper.UserCreditMapper;
import com.baolong.wsw.mapper.UserMapper;
import com.baolong.wsw.mapper.WalletAccountMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理后台服务：概览、用户、内容审核、举报、争议、任务、发币、配置、日志。
 *
 * <p>所有写操作都会落一条 {@code admin_op_log}，事后能查是谁做的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    /** 发币用的唯一序号，拼在幂等键里，保证每次发放都是一次新流水 */
    private static final AtomicLong ISSUE_SEQ = new AtomicLong();

    private final UserMapper userMapper;
    private final UserCreditMapper userCreditMapper;
    private final TaskMapper taskMapper;
    private final TaskClaimMapper taskClaimMapper;
    private final PostMapper postMapper;
    private final ReportMapper reportMapper;
    private final DisputeMapper disputeMapper;
    private final AuditItemMapper auditItemMapper;
    private final AdminOpLogMapper adminOpLogMapper;
    private final WalletAccountMapper walletAccountMapper;
    private final com.baolong.wsw.mapper.WalletTransactionMapper walletTransactionMapper;
    private final com.baolong.wsw.mapper.LoginLogMapper loginLogMapper;
    private final WalletService walletService;
    private final CreditService creditService;
    private final ConfigService configService;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ 概览

    public AdminDto.OverviewVO overview() {
        long userCount = userMapper.selectCount(null);
        long taskCount = taskMapper.selectCount(null);
        long postCount = postMapper.selectCount(null);

        long settled = countTaskStatus(TaskStatus.SETTLED);
        long disputed = countTaskStatus(TaskStatus.DISPUTED);
        double finishRate = percent(settled, taskCount);
        double disputeRate = percent(disputed, taskCount);

        // 发行 = 进入系统的钱（GRANT + 正向冲正）；回收 = 真正离开系统的钱。
        // 冲正也算进来，否则做过一次冲正后恒等式必然差出冲正金额。
        // 还要扣掉「违约金补偿」：罚款从 A 扣走又补给 B，钱没离开系统，
        // 若整笔都算成回收，看板就会一直差出这部分（之前差 60 就是这个原因）。
        long coinIssued = walletTransactionMapper.sumAbsByType(TransType.GRANT.name())
                + walletTransactionMapper.sumPositiveByType(TransType.REVERSAL.name());
        long penaltyTotal = walletTransactionMapper.sumAbsByType(TransType.PENALTY.name());
        // 罚款补偿的 originType 有两种：旧数据是 PENALTY，现在的放弃流程是 ABANDON，
        // 两种都要扣掉，否则做过一次放弃后看板就凭空多出这笔「回收」。
        long penaltyCompensated = walletTransactionMapper.sumIncomeByOrigin("PENALTY")
                + walletTransactionMapper.sumIncomeByOrigin("ABANDON");
        long coinRecycled = Math.max(0, penaltyTotal - penaltyCompensated)
                + walletTransactionMapper.sumNegativeAbsByType(TransType.REVERSAL.name());

        long circulating = 0;
        long frozen = 0;
        long debt = 0;
        for (WalletAccount account : walletAccountMapper.selectList(null)) {
            circulating += nullToZero(account.getAvailable());
            frozen += nullToZero(account.getFrozen());
            debt += nullToZero(account.getDebt());
        }

        return new AdminDto.OverviewVO(userCount, taskCount, postCount,
                finishRate, disputeRate,
                coinIssued, circulating, frozen, coinRecycled, debt);
    }

    /** 百分比，保留一位小数 */
    private static double percent(long part, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(part * 1000.0 / total) / 10.0;
    }

    private static long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    /** 近 N 天趋势：日活 / 发布量 / 接单量，缺失日期补 0 */
    public List<AdminDto.TrendPoint> trend(int days) {
        int span = Math.max(1, Math.min(days, 30));
        LocalDate from = LocalDate.now().minusDays(span - 1L);
        Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
        Map<LocalDate, java.util.Set<Long>> activeUsers = new HashMap<>();
        for (int i = 0; i < span; i++) {
            buckets.put(from.plusDays(i), new long[3]);
        }
        for (Task task : taskMapper.selectList(
                Wrappers.<Task>lambdaQuery().ge(Task::getCreatedAt, from.atStartOfDay()))) {
            accumulate(buckets, task.getCreatedAt(), 1);
        }
        for (com.baolong.wsw.entity.TaskClaim claim : taskClaimMapper.selectList(
                Wrappers.<com.baolong.wsw.entity.TaskClaim>lambdaQuery()
                        .ge(com.baolong.wsw.entity.TaskClaim::getClaimedAt, from.atStartOfDay()))) {
            accumulate(buckets, claim.getClaimedAt(), 2);
        }
        // 日活 = 当天登录成功过的去重用户数
        for (com.baolong.wsw.entity.LoginLog login : loginLogMapper.selectList(
                Wrappers.<com.baolong.wsw.entity.LoginLog>lambdaQuery()
                        .ge(com.baolong.wsw.entity.LoginLog::getCreatedAt, from.atStartOfDay())
                        .eq(com.baolong.wsw.entity.LoginLog::getSuccess, Boolean.TRUE))) {
            if (login.getCreatedAt() == null || login.getUserId() == null) {
                continue;
            }
            LocalDate day = login.getCreatedAt().toLocalDate();
            long[] counts = buckets.get(day);
            if (counts == null) {
                continue;
            }
            if (activeUsers.computeIfAbsent(day, k -> new java.util.HashSet<>()).add(login.getUserId())) {
                counts[0] = activeUsers.get(day).size();
            }
        }
        List<AdminDto.TrendPoint> result = new ArrayList<>(span);
        buckets.forEach((date, counts) -> result.add(
                new AdminDto.TrendPoint(date.toString(), counts[0], counts[1], counts[2])));
        return result;
    }

    // ------------------------------------------------------------------ 用户

    public PageResult<AdminDto.AdminUserVO> users(String keyword, String status, int page, int size) {
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery().orderByDesc(User::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getPhone, keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(User::getStatus, status.toUpperCase());
        }
        Page<User> p = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<AdminDto.AdminUserVO> list = new ArrayList<>(p.getRecords().size());
        for (User user : p.getRecords()) {
            UserCredit credit = userCreditMapper.selectById(user.getId());
            // 发布 / 接单 / 被举报：管理端表格要展示，之前这三个字段没返回，前端只能显示「/」和「—」
            Long published = taskMapper.selectCount(
                    Wrappers.<Task>lambdaQuery().eq(Task::getPublisherId, user.getId()));
            Long claimed = taskMapper.selectCount(
                    Wrappers.<Task>lambdaQuery().eq(Task::getClaimantId, user.getId()));
            Long reported = countReported(user.getId());
            list.add(new AdminDto.AdminUserVO(
                    user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(), user.getPhone(),
                    user.getRole(), user.getStatus(), user.getAuthStatus(),
                    credit == null || credit.getCreditScore() == null ? 100
                            : (int) Math.round(credit.getCreditScore().doubleValue()),
                    credit == null || credit.getFinishedClaims() == null ? 0 : credit.getFinishedClaims(),
                    user.getRealName(),
                    user.getStudentNo(),
                    user.getCertImage(),
                    published == null ? 0 : published.intValue(),
                    claimed == null ? 0 : claimed.intValue(),
                    reported == null ? 0 : reported.intValue(),
                    user.getCreatedAt()));
        }
        return new PageResult<>(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    /** 被举报次数：针对他发布的任务 + 他发的帖子，已驳回的不算 */
    private long countReported(Long userId) {
        Long count = reportMapper.selectCount(Wrappers.<Report>lambdaQuery()
                .ne(Report::getStatus, "REJECTED")
                .and(w -> w.inSql(Report::getTaskId, "SELECT id FROM task WHERE publisher_id = " + userId)
                        .or()
                        .inSql(Report::getPostId, "SELECT id FROM post WHERE author_id = " + userId)));
        return count == null ? 0 : count;
    }

    @Transactional(rollbackFor = Exception.class)
    public void ban(Long adminId, Long userId, String reason) {
        User user = requireUser(userId);
        if (isSuperAdmin(user)) {
            throw BizException.forbidden("不能封禁超级管理员");
        }
        user.setStatus(UserStatus.BANNED.name());
        userMapper.updateById(user);
        opLog(adminId, "BAN_USER", "user:" + userId,
                "封禁用户 " + user.getUsername() + (reason == null ? "" : "，原因：" + reason));
        notificationService.pushSystem(userId, "账号已被封禁", reason == null ? "如有疑问请联系管理员" : reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unban(Long adminId, Long userId) {
        User user = requireUser(userId);
        user.setStatus(UserStatus.NORMAL.name());
        userMapper.updateById(user);
        opLog(adminId, "UNBAN_USER", "user:" + userId, "解封用户 " + user.getUsername());
        notificationService.pushSystem(userId, "账号已恢复正常", "现在可以正常接单和发单了");
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long adminId, Long userId) {
        User user = requireUser(userId);
        if (isSuperAdmin(user)) {
            throw BizException.forbidden("不能删除超级管理员");
        }
        // 软删除：置为 BANNED 并清掉可识别信息，保留外键引用
        user.setStatus(UserStatus.BANNED.name());
        user.setNickname("已注销用户");
        user.setPhone(null);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        opLog(adminId, "DELETE_USER", "user:" + userId, "注销用户 " + user.getUsername());
    }

    /** 实名认证审核 */
    @Transactional(rollbackFor = Exception.class)
    public void auditCert(Long adminId, Long userId, boolean approve, String reason) {
        User user = requireUser(userId);
        if (!AuthStatus.PENDING.name().equals(user.getAuthStatus())) {
            throw BizException.conflict("该用户没有待审核的实名认证申请");
        }
        if (approve) {
            user.setAuthStatus(AuthStatus.APPROVED.name());
            user.setCertRejectReason(null);
            notificationService.pushSystem(userId, "实名认证通过", "现在可以使用全部功能了");
        } else {
            user.setAuthStatus(AuthStatus.REJECTED.name());
            user.setCertRejectReason(reason);
            notificationService.pushSystem(userId, "实名认证未通过",
                    reason == null ? "请核对学号与姓名后重新提交" : reason);
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        opLog(adminId, approve ? "APPROVE_CERT" : "REJECT_CERT", "user:" + userId, reason);
    }

    // ------------------------------------------------------------------ 内容审核

    public PageResult<AdminDto.AuditVO> audits(String status, int page, int size) {
        LambdaQueryWrapper<AuditItem> wrapper = Wrappers.<AuditItem>lambdaQuery()
                .orderByDesc(AuditItem::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(AuditItem::getStatus, status.toUpperCase());
        }
        Page<AuditItem> p = auditItemMapper.selectPage(new Page<>(page, size), wrapper);
        List<AdminDto.AuditVO> list = new ArrayList<>(p.getRecords().size());
        for (AuditItem item : p.getRecords()) {
            list.add(new AdminDto.AuditVO(item.getId(), item.getTargetType(), item.getTargetId(),
                    item.getTitle(), item.getContent(), item.getHitReason(),
                    brief(item.getAuthorId()), item.getStatus(), item.getCreatedAt()));
        }
        return new PageResult<>(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleAudit(Long adminId, Long id, boolean approve, String reason) {
        AuditItem item = auditItemMapper.selectById(id);
        if (item == null) {
            throw BizException.notFound("审核项不存在");
        }
        if (!"PENDING".equals(item.getStatus())) {
            throw BizException.conflict("该项已处理");
        }
        item.setStatus(approve ? "APPROVED" : "REJECTED");
        item.setHandledAt(LocalDateTime.now());
        auditItemMapper.updateById(item);
        if (!approve && item.getTargetId() != null) {
            takeDown(item.getTargetType(), item.getTargetId(), reason);
            if (item.getAuthorId() != null) {
                notificationService.pushSystem(item.getAuthorId(), "内容已被下架",
                        reason == null ? "内容不符合社区规范" : reason);
            }
        }
        // 审核队列与举报队列是两个独立队列，这里不再互相回写，
        // 避免「处理完一边、另一边还挂着 PENDING」。同一条内容若两个队列都有，
        // 各自独立处理即可。
        opLog(adminId, approve ? "PASS_AUDIT" : "REJECT_AUDIT",
                item.getTargetType() + ":" + item.getTargetId(), reason);
    }

    // ------------------------------------------------------------------ 举报

    public PageResult<AdminDto.ReportVO> reports(String status, int page, int size) {
        LambdaQueryWrapper<Report> wrapper = Wrappers.<Report>lambdaQuery()
                .orderByDesc(Report::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Report::getStatus, status.toUpperCase());
        }
        Page<Report> p = reportMapper.selectPage(new Page<>(page, size), wrapper);
        List<AdminDto.ReportVO> list = new ArrayList<>(p.getRecords().size());
        for (Report report : p.getRecords()) {
            list.add(new AdminDto.ReportVO(report.getId(), report.getType(), report.getTaskId(),
                    report.getPostId(), report.getTargetTitle(), brief(report.getReporterId()),
                    report.getReason(), splitEvidence(report.getEvidence()),
                    // 前端枚举是 PENDING/RESOLVED/REJECTED，库里存的是 HANDLED
                    "HANDLED".equals(report.getStatus()) ? "RESOLVED" : report.getStatus(),
                    report.getResult(), report.getCreatedAt()));
        }
        return new PageResult<>(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    /**
     * 处理举报。
     *
     * @param pass    true=举报成立，false=不成立
     * @param result  处理说明，展示给举报人和被举报人
     * @param penalty 成立时可选的屋币扣罚（从被举报人可用余额扣，不足挂欠款）
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long adminId, Long id, boolean pass, String result, Long penalty) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw BizException.notFound("举报不存在");
        }
        if (!"PENDING".equals(report.getStatus())) {
            throw BizException.conflict("该举报已处理过了");
        }
        Long authorId = resolveAuthor(report);
        String detail = (result == null || result.isBlank())
                ? (pass ? "举报成立" : "举报不成立") : result;

        if (pass) {
            if (report.getTaskId() != null) {
                offlineTaskInternal(adminId, report.getTaskId(), "举报成立：" + detail);
            } else if (report.getPostId() != null) {
                takeDown("POST", report.getPostId(), "举报成立：" + detail);
            }
            if (penalty != null && penalty > 0 && authorId != null) {
                walletService.penalty(authorId, penalty, com.baolong.wsw.enums.BizType.ADMIN,
                        report.getId(), "举报处罚", "REPORT", report.getId());
            }
            if (authorId != null) {
                notificationService.pushSystem(authorId, "你发布的内容被举报成立",
                        detail + (penalty != null && penalty > 0 ? "，扣罚 " + penalty + " 屋币" : ""));
            }
            report.setStatus("HANDLED");
        } else {
            report.setStatus("REJECTED");
            // 不成立也要告诉被举报人一声：否则他永远不知道自己被人举报过、
            // 也不知道平台已经查清了，这跟「石沉大海」没区别。
            if (authorId != null) {
                notificationService.pushSystem(authorId, "关于你的举报已核实",
                        "经核实该举报不成立，你的内容未受影响。" + detail);
            }
        }
        report.setResult(detail);
        report.setHandledAt(LocalDateTime.now());
        reportMapper.updateById(report);
        if (report.getReporterId() != null) {
            notificationService.pushSystem(report.getReporterId(),
                    pass ? "你的举报已成立" : "你的举报未成立", detail);
        }
        // 举报队列不再回写审核队列：两个队列各自独立终结
        opLog(adminId, "HANDLE_REPORT", "report:" + id, (pass ? "成立 " : "不成立 ") + detail);
    }

    /** 用户摘要（头像 + 昵称 + 信用分），列表里渲染作者 / 举报人用 */
    private TaskDto.UserBrief brief(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        UserCredit credit = userCreditMapper.selectById(userId);
        int score = credit == null || credit.getCreditScore() == null
                ? 100 : (int) Math.round(credit.getCreditScore().doubleValue());
        return new TaskDto.UserBrief(user.getId(), user.getNickname(), user.getAvatar(), score);
    }

    private static List<String> splitEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(evidence.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    // ------------------------------------------------------------------ 争议

    public PageResult<AdminDto.DisputeVO> disputes(String status, int page, int size) {
        LambdaQueryWrapper<Dispute> wrapper = Wrappers.<Dispute>lambdaQuery()
                .orderByDesc(Dispute::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Dispute::getStatus, status.toUpperCase());
        }
        Page<Dispute> p = disputeMapper.selectPage(new Page<>(page, size), wrapper);
        List<AdminDto.DisputeVO> list = new ArrayList<>(p.getRecords().size());
        for (Dispute dispute : p.getRecords()) {
            Task task = taskMapper.selectById(dispute.getTaskId());
            list.add(new AdminDto.DisputeVO(dispute.getId(), dispute.getTaskId(),
                    task == null ? null : task.getTitle(),
                    brief(dispute.getPublisherId()),
                    brief(dispute.getTakerId()),
                    dispute.getFrozenAmount(),
                    dispute.getInitiatorId(),
                    brief(dispute.getInitiatorId()),
                    dispute.getReason(), dispute.getEvidence(), dispute.getStatus(),
                    dispute.getResult(), dispute.getRemark(), dispute.getCreatedAt()));
        }
        return new PageResult<>(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    /**
     * 删除已裁决的历史争议记录（只清记录，不回滚钱）。
     *
     * <p>未裁决的不允许删——钱还冻着，删了就没有凭据可查了。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDispute(Long adminId, Long id) {
        Dispute dispute = disputeMapper.selectById(id);
        if (dispute == null) {
            throw BizException.notFound("争议不存在");
        }
        if (!"JUDGED".equals(dispute.getStatus())) {
            throw BizException.conflict("争议尚未裁决，不能删除");
        }
        disputeMapper.deleteById(id);
        opLog(adminId, "DELETE_DISPUTE", "dispute:" + id,
                "taskId=" + dispute.getTaskId() + ", result=" + dispute.getResult());
    }

    /**
     * 争议裁决：按结果拆账。
     *
     * <p>PUBLISHER → 冻结退回发布人、任务取消；TAKER → 全额给接单方；
     * PARTIAL → 各一半；INSUFFICIENT → 证据不足，冻结退回、任务取消。
     */
    @Transactional(rollbackFor = Exception.class)
    public void judgeDispute(Long adminId, Long id, String result, String remark) {
        Dispute dispute = disputeMapper.selectById(id);
        if (dispute == null) {
            throw BizException.notFound("争议不存在");
        }
        if ("JUDGED".equals(dispute.getStatus())) {
            throw BizException.conflict("该争议已裁决");
        }
        Task task = taskMapper.selectById(dispute.getTaskId());
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        String verdict = result == null ? "INSUFFICIENT" : result.toUpperCase();
        long frozen = dispute.getFrozenAmount() == null ? task.getReward() : dispute.getFrozenAmount();
        Long publisherId = dispute.getPublisherId();
        Long takerId = dispute.getTakerId();

        switch (verdict) {
            case "TAKER" -> {
                if (takerId == null) {
                    // 数据异常：没有接单方就没有「判给接单方」，退回发布人更安全，别把钱吞掉
                    walletService.unfreeze(publisherId, frozen, com.baolong.wsw.enums.BizType.DISPUTE,
                            task.getId(), "争议裁决退回：" + task.getTitle(), "DISPUTE", dispute.getId());
                    requireResolved(task.getId(), TaskStatus.CANCELLED.name(), remark);
                } else {
                    walletService.consumeFrozen(publisherId, frozen);
                    walletService.income(takerId, frozen, com.baolong.wsw.enums.BizType.DISPUTE,
                            task.getId(), "争议裁决所得：" + task.getTitle(), "DISPUTE", dispute.getId());
                    requireResolved(task.getId(), TaskStatus.SETTLED.name(), remark);
                }
            }
            case "PARTIAL" -> {
                if (takerId == null) {
                    walletService.unfreeze(publisherId, frozen, com.baolong.wsw.enums.BizType.DISPUTE,
                            task.getId(), "争议裁决退回：" + task.getTitle(), "DISPUTE", dispute.getId());
                    requireResolved(task.getId(), TaskStatus.CANCELLED.name(), remark);
                } else {
                    long half = frozen / 2;
                    long rest = frozen - half;
                    walletService.unfreeze(publisherId, rest, com.baolong.wsw.enums.BizType.DISPUTE,
                            task.getId(), "争议部分退回：" + task.getTitle(), "DISPUTE", dispute.getId());
                    walletService.consumeFrozen(publisherId, half);
                    walletService.income(takerId, half, com.baolong.wsw.enums.BizType.DISPUTE,
                            task.getId(), "争议部分补偿：" + task.getTitle(), "DISPUTE", dispute.getId());
                    requireResolved(task.getId(), TaskStatus.SETTLED.name(), remark);
                }
            }
            default -> {
                // PUBLISHER / INSUFFICIENT：钱退回发布人，任务终止
                walletService.unfreeze(publisherId, frozen, com.baolong.wsw.enums.BizType.DISPUTE,
                        task.getId(), "争议裁决退回：" + task.getTitle(), "DISPUTE", dispute.getId());
                requireResolved(task.getId(), TaskStatus.CANCELLED.name(), remark);
            }
        }

        dispute.setStatus("JUDGED");
        dispute.setResult(verdict);
        dispute.setRemark(remark);
        dispute.setJudgedAt(LocalDateTime.now());
        disputeMapper.updateById(dispute);

        if (publisherId != null) {
            creditService.refresh(publisherId);
            notificationService.pushSystem(publisherId, "争议已裁决",
                    "裁决结果：" + judgeText(verdict) + "。" + Objects.toString(remark, ""));
        }
        if (takerId != null) {
            creditService.refresh(takerId);
            notificationService.pushSystem(takerId, "争议已裁决",
                    "裁决结果：" + judgeText(verdict) + "。" + Objects.toString(remark, ""));
        }
        opLog(adminId, "JUDGE_DISPUTE", "dispute:" + id, verdict + " " + Objects.toString(remark, ""));
    }

    /** 改任务状态并校验影响行数：0 行说明任务状态已被别人改过，整笔事务回滚 */
    private void requireResolved(Long taskId, String targetStatus, String remark) {
        if (taskMapper.resolveDispute(taskId, targetStatus, remark) == 0) {
            throw BizException.conflict("任务状态已变更，无法完成裁决，请刷新后重试");
        }
    }

    // ------------------------------------------------------------------ 任务

    public PageResult<AdminDto.AdminTaskVO> tasks(String keyword, String status, int page, int size) {
        LambdaQueryWrapper<Task> wrapper = Wrappers.<Task>lambdaQuery().orderByDesc(Task::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Task::getTitle, keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Task::getStatus, com.baolong.wsw.dto.TaskWire.fromWire(status));
        }
        Page<Task> p = taskMapper.selectPage(new Page<>(page, size), wrapper);
        List<AdminDto.AdminTaskVO> list = new ArrayList<>(p.getRecords().size());
        for (Task task : p.getRecords()) {
            list.add(new AdminDto.AdminTaskVO(task.getId(), task.getTitle(), task.getType(),
                    task.getReward(), com.baolong.wsw.dto.TaskWire.toWire(task.getStatus()),
                    task.getLocation(), task.getDeadline(),
                    task.getPublisherId(), brief(task.getPublisherId()),
                    task.getClaimantId(), brief(task.getClaimantId()),
                    task.getCreatedAt()));
        }
        return new PageResult<>(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    /** 强制下架任务：解冻报酬并终止 */
    @Transactional(rollbackFor = Exception.class)
    public void offlineTask(Long adminId, Long taskId, String reason) {
        offlineTaskInternal(adminId, taskId, reason);
    }

    private void offlineTaskInternal(Long adminId, Long taskId, String reason) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        if (isFinal(task.getStatus())) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID, "任务已结束，无法下架");
        }
        if (Boolean.TRUE.equals(holdsFrozen(task.getStatus()))) {
            walletService.unfreeze(task.getPublisherId(), task.getReward(),
                    com.baolong.wsw.enums.BizType.ADMIN, task.getId(),
                    "任务下架退回：" + task.getTitle(), "TASK_OFFLINE", task.getId());
        }
        String finalReason = reason == null ? "管理员下架" : reason;
        // 不能用 cancelByPublisher：它带 status IN ('WAITING','IN_PROGRESS') 的守卫，
        // 争议中的任务会更新 0 行、接口却返回成功。
        int rows = taskMapper.adminTakeDown(taskId, finalReason);
        if (rows == 0) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID, "任务已结束，无法下架");
        }
        // 争议中的任务被下架：把待处理争议一并关掉，否则后台会永远挂着一条裁不了的争议
        List<Dispute> pending = disputeMapper.selectList(Wrappers.<Dispute>lambdaQuery()
                .eq(Dispute::getTaskId, taskId)
                .eq(Dispute::getStatus, "PENDING"));
        for (Dispute dispute : pending) {
            dispute.setStatus("JUDGED");
            dispute.setResult("INSUFFICIENT");
            dispute.setRemark("任务已被管理员下架，争议按证据不足关闭");
            dispute.setJudgedAt(LocalDateTime.now());
            disputeMapper.updateById(dispute);
        }
        notificationService.pushSystem(task.getPublisherId(), "任务已被下架", finalReason);
        opLog(adminId, "OFFLINE_TASK", "task:" + taskId, finalReason);
    }

    // ------------------------------------------------------------------ 帖子管理

    /** 帖子列表（管理端）：按关键词搜标题、按状态筛，分页 */
    public PageResult<AdminDto.AdminPostVO> posts(String keyword, String status, int page, int size) {
        int pn = Math.max(1, page);
        int ps = Math.max(1, Math.min(size, 50));
        var wrapper = Wrappers.<Post>lambdaQuery().orderByDesc(Post::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Post::getTitle, keyword.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Post::getStatus, status.trim().toUpperCase());
        }
        Page<Post> result = postMapper.selectPage(new Page<>(pn, ps), wrapper);
        // 一次性把这页帖子「待处理举报数」查出来，避免每条帖子单独查一次
        List<Long> postIds = result.getRecords().stream().map(Post::getId).toList();
        java.util.Map<Long, Integer> reportCount = new java.util.HashMap<>();
        if (!postIds.isEmpty()) {
            List<Report> pending = reportMapper.selectList(Wrappers.<Report>lambdaQuery()
                    .in(Report::getPostId, postIds)
                    .eq(Report::getStatus, "PENDING"));
            for (Report r : pending) {
                reportCount.merge(r.getPostId(), 1, Integer::sum);
            }
        }
        List<AdminDto.AdminPostVO> list = result.getRecords().stream()
                .map(post -> new AdminDto.AdminPostVO(
                        post.getId(),
                        post.getTitle(),
                        post.getContent(),
                        brief(post.getAuthorId()),
                        splitTags(post.getTags()),
                        post.getReplyCount(),
                        post.getLikeCount(),
                        post.getStatus(),
                        reportCount.getOrDefault(post.getId(), 0),
                        post.getCreatedAt()))
                .toList();
        return PageResult.of(list, result.getTotal(), pn, ps);
    }

    /** 下架帖子：正常 → 已下架，并通知作者 */
    @Transactional(rollbackFor = Exception.class)
    public void offlinePost(Long adminId, Long postId, String reason) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw BizException.notFound("帖子不存在");
        }
        if (!"NORMAL".equals(post.getStatus())) {
            throw BizException.conflict("该帖子当前不是正常状态，无需下架");
        }
        int rows = postMapper.update(null, Wrappers.<Post>lambdaUpdate()
                .eq(Post::getId, postId)
                .eq(Post::getStatus, "NORMAL")
                .set(Post::getStatus, "OFFLINE")
                .set(Post::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw BizException.conflict("帖子状态已变化，请刷新后重试");
        }
        String finalReason = reason == null || reason.isBlank() ? "内容不符合社区规范" : reason.trim();
        notificationService.pushSystem(post.getAuthorId(), "帖子已被下架", finalReason);
        opLog(adminId, "OFFLINE_POST", "post:" + postId, finalReason);
    }

    /** 恢复帖子：已下架 → 正常（误判时的回滚路径） */
    @Transactional(rollbackFor = Exception.class)
    public void restorePost(Long adminId, Long postId, String reason) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw BizException.notFound("帖子不存在");
        }
        if (!"OFFLINE".equals(post.getStatus())) {
            throw BizException.conflict("只有已下架的帖子才能恢复");
        }
        int rows = postMapper.update(null, Wrappers.<Post>lambdaUpdate()
                .eq(Post::getId, postId)
                .eq(Post::getStatus, "OFFLINE")
                .set(Post::getStatus, "NORMAL")
                .set(Post::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw BizException.conflict("帖子状态已变化，请刷新后重试");
        }
        String finalReason = reason == null || reason.isBlank() ? "管理员复核后恢复" : reason.trim();
        notificationService.pushSystem(post.getAuthorId(), "帖子已恢复", finalReason);
        opLog(adminId, "RESTORE_POST", "post:" + postId, finalReason);
    }

    /** 帖子的 tags 在库里是英文逗号串 */
    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (String tag : tags.split(",")) {
            if (!tag.isBlank()) {
                list.add(tag.trim());
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ 发币 / 冲正

    @Transactional(rollbackFor = Exception.class)
    public void issueCoins(Long adminId, Long userId, long amount, String remark) {
        requireUser(userId);
        // 幂等键是 transType:originType:originId:userId。
        // 以前这里固定用 adminId 当 originId —— 同一个管理员第二次给同一个人发币时，
        // 流水被当成「重复请求」直接跳过，但 opLog 和通知照发，用户被告知到账却没钱。
        // 每次发币生成一个唯一序号即可。
        long originId = System.currentTimeMillis() * 1000
                + ISSUE_SEQ.incrementAndGet() % 1000;
        walletService.grant(userId, amount, com.baolong.wsw.enums.BizType.ADMIN, userId,
                "管理员发币", "ADMIN_ISSUE", originId);
        opLog(adminId, "ISSUE_COINS", "user:" + userId, amount + " 屋币 " + Objects.toString(remark, ""));
        notificationService.pushFund(userId, "收到管理员发放的屋币", amount + " 屋币已到账", null);
    }

    /**
     * 发币（支持按登录名定位用户）。
     * <p>后台管理员通常只知道昵称/登录名、记不住数字 ID，所以允许传 username。
     */
    @Transactional(rollbackFor = Exception.class)
    public void issueCoins(Long adminId, Long userId, String username, long amount, String remark) {
        Long target = userId != null ? userId : requireUserIdByUsername(username);
        issueCoins(adminId, target, amount, remark);
    }

    private Long requireUserIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw BizException.badRequest("请指定发币对象");
        }
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username.trim()));
        if (user == null) {
            throw BizException.notFound("用户不存在：" + username);
        }
        return user.getId();
    }

    /**
     * 裁决结果的中文名。通知里给用户看的是人话，不是 {@code PUBLISHER} 这种枚举名。
     */
    private static String judgeText(String verdict) {
        if (verdict == null) {
            return "证据不足";
        }
        return switch (verdict) {
            case "PUBLISHER" -> "支持委托方，报酬全额退回";
            case "TAKER" -> "支持接单方，报酬全额支付";
            case "PARTIAL" -> "双方责任，报酬对半分摊";
            default -> "证据不足，任务关闭";
        };
    }

    @Transactional(rollbackFor = Exception.class)
    public int issueCoinsBatch(Long adminId, List<Long> userIds, long amount, String remark) {
        int success = 0;
        for (Long userId : userIds) {
            try {
                issueCoins(adminId, userId, amount, remark);
                success++;
            } catch (Exception e) {
                log.warn("批量发币跳过 user={}: {}", userId, e.getMessage());
            }
        }
        opLog(adminId, "ISSUE_COINS_BATCH", "users:" + userIds.size(), "成功 " + success + " 个");
        return success;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reverseCoins(Long adminId, Long transactionId, String reason) {
        WalletTransaction origin = walletTransactionMapper.selectById(transactionId);
        if (origin == null) {
            throw BizException.notFound("原流水不存在");
        }
        if (TransType.REVERSAL.name().equals(origin.getTransType())) {
            throw BizException.conflict("冲正流水本身不能再被冲正");
        }
        // 冻结 / 解冻不是「收支」，而是同一个账户内部 available 与 frozen 的搬家。
        // 把 FREEZE(-10) 冲成 +10 会让可用余额涨回去、frozen 仍占着，
        // 之后任务结算时冻结额不足 → 与「结算只 warn」叠在一起就是凭空印钱。
        // 这类账必须走任务流程（取消 / 过期 / 裁决）而不是冲正。
        if (TransType.FREEZE.name().equals(origin.getTransType())
                || TransType.UNFREEZE.name().equals(origin.getTransType())) {
            throw BizException.conflict("冻结/解冻流水是账务镜像，不支持冲正；请通过任务流程处理");
        }
        Long reversed = walletTransactionMapper.selectCount(
                Wrappers.<WalletTransaction>lambdaQuery()
                        .eq(WalletTransaction::getTransType, TransType.REVERSAL.name())
                        .eq(WalletTransaction::getOriginType, "TX")
                        .eq(WalletTransaction::getOriginId, transactionId));
        if (reversed != null && reversed > 0) {
            throw BizException.conflict("这条流水已经冲正过了，不能重复冲正");
        }
        long amount = origin.getAmount() == null ? 0 : origin.getAmount();
        if (amount == 0) {
            throw BizException.badRequest("该流水金额为 0，无需冲正");
        }
        // 反向操作：原流水是 -10 就补回 +10，原流水是 +10 就扣回 -10
        long delta = -amount;
        walletService.reversal(origin.getUserId(), delta, com.baolong.wsw.enums.BizType.ADMIN,
                origin.getId(), "冲正原流水 #" + transactionId,
                Objects.toString(reason, "管理员冲正"), "TX", transactionId);
        opLog(adminId, "REVERSE_COINS", "tx:" + transactionId, delta + " 屋币 " + Objects.toString(reason, ""));
        notificationService.pushFund(origin.getUserId(), "资金被冲正",
                "管理员冲正了一笔流水，金额变动 " + delta + " 屋币", null);
    }

    /**
     * 资金流水查询（后台冲正选单用）。
     *
     * <p>只看「最近 20 条」是不够的：要冲的可能是三天前发错的那笔。
     * 所以这里支持按用户、类型、关键词（流水号或业务标题）过滤，并分页。
     *
     * @param userId   只看某个用户的流水（可空）
     * @param transType GRANT / FREEZE / ... （可空）
     * @param keyword  纯数字按流水号精确匹配，否则按业务标题模糊匹配（可空）
     */
    public PageResult<AdminDto.RecentTransactionVO> transactions(
            Long userId, String transType, String keyword, int page, int size) {
        LambdaQueryWrapper<WalletTransaction> wrapper = Wrappers.<WalletTransaction>lambdaQuery()
                .orderByDesc(WalletTransaction::getId);
        if (userId != null) {
            wrapper.eq(WalletTransaction::getUserId, userId);
        }
        if (transType != null && !transType.isBlank()) {
            wrapper.eq(WalletTransaction::getTransType, transType.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            if (kw.matches("\\d+")) {
                // 纯数字：当成流水号，直接定位那一条
                wrapper.eq(WalletTransaction::getId, Long.parseLong(kw));
            } else {
                wrapper.like(WalletTransaction::getBizTitle, kw);
            }
        }
        Page<WalletTransaction> p = walletTransactionMapper.selectPage(new Page<>(page, size), wrapper);

        // 批量取用户，避免逐条查（N+1）
        List<Long> userIds = p.getRecords().stream()
                .map(WalletTransaction::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> nicknames = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userMapper.selectBatchIds(userIds)) {
                nicknames.put(user.getId(), user.getNickname());
            }
        }

        List<AdminDto.RecentTransactionVO> list = new ArrayList<>(p.getRecords().size());
        for (WalletTransaction tx : p.getRecords()) {
            list.add(new AdminDto.RecentTransactionVO(
                    tx.getId(),
                    tx.getUserId(),
                    nicknames.getOrDefault(tx.getUserId(), "已注销用户"),
                    tx.getTransType(),
                    transLabel(tx.getTransType()),
                    tx.getAmount() == null ? 0 : tx.getAmount(),
                    tx.getBizTitle(),
                    tx.getCreatedAt()));
        }
        return new PageResult<>(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    /** 流水类型的中文名（给后台列表看，不是给前端枚举用） */
    private static String transLabel(String transType) {
        if (transType == null) {
            return "未知";
        }
        return switch (transType) {
            case "GRANT" -> "发放";
            case "FREEZE" -> "冻结";
            case "UNFREEZE" -> "解冻";
            case "INCOME" -> "收入";
            case "PAY" -> "支付";
            case "PENALTY" -> "扣罚";
            case "REVERSAL" -> "冲正";
            default -> transType;
        };
    }

    /**
     * 全平台对账：返回「账面 ≠ 流水」的异常账户；返回空列表表示全站平衡。
     *
     * <p>恒等式是 {@code 可用 - 欠款 == SUM(流水.amount)}，<b>不含冻结</b>：
     * 冻结时可用已经减了、流水也记了 -amount，冻结额只是「这笔钱被哪条任务占着」的镜像，
     * 它不进流水。若把 frozen 加进账面，每个有进行中任务的用户都会被误判为异常。
     */
    public List<AdminDto.AccountAuditVO> auditCoins() {
        List<AdminDto.AccountAuditVO> abnormal = new ArrayList<>();
        for (WalletAccount account : walletAccountMapper.selectList(null)) {
            long balance = nullToZero(account.getAvailable()) - nullToZero(account.getDebt());
            long calculated = walletTransactionMapper.sumAmount(account.getId());
            if (balance != calculated) {
                User user = userMapper.selectById(account.getUserId());
                abnormal.add(new AdminDto.AccountAuditVO(
                        account.getUserId(),
                        user == null ? "已注销用户" : user.getUsername(),
                        balance, calculated));
            }
        }
        return abnormal;
    }

    /** 对账：账面 vs 流水 */
    public Map<String, Object> checkWallet(Long userId) {
        Map<String, Object> result = new HashMap<>();
        WalletAccount account = walletService.requireAccount(userId);
        result.put("userId", userId);
        result.put("available", account.getAvailable());
        result.put("frozen", account.getFrozen());
        result.put("debt", account.getDebt());
        result.put("balanced", walletService.checkInvariant(userId));
        return result;
    }

    // ------------------------------------------------------------------ 配置 / 日志

    public Map<String, String> config() {
        return configService.all();
    }

    /** 允许管理员在后台改的配置项白名单（防止前端乱传把库写脏） */
    private static final java.util.Set<String> EDITABLE_CONFIG = java.util.Set.of(
            "initGrant", "maxHolding", "grantLimit",
            "autoConfirmHours", "claimTimeoutHours", "penaltyCancel", "penaltyNoShow");

    /**
     * 批量保存配置。
     *
     * <p>前端是把整张配置表单一次性提交上来的，所以这里收 Map 而不是单个 key/value；
     * 值统一转成字符串入库（{@code system_config.value} 是 VARCHAR）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(Long adminId, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            throw BizException.badRequest("配置内容不能为空");
        }
        values.forEach((key, raw) -> {
            if (!EDITABLE_CONFIG.contains(key)) {
                return;
            }
            String value = raw == null ? "" : String.valueOf(raw);
            configService.update(key, value);
            opLog(adminId, "UPDATE_CONFIG", "config:" + key, value);
        });
    }

    public PageResult<AdminDto.OpLogVO> logs(int page, int size) {
        Page<AdminOpLog> p = adminOpLogMapper.selectPage(new Page<>(page, size),
                Wrappers.<AdminOpLog>lambdaQuery().orderByDesc(AdminOpLog::getId));
        List<AdminDto.OpLogVO> list = new ArrayList<>(p.getRecords().size());
        for (AdminOpLog item : p.getRecords()) {
            list.add(new AdminDto.OpLogVO(item.getId(), item.getAdminId(), item.getAction(),
                    item.getTarget(), item.getDetail(), item.getCreatedAt()));
        }
        return new PageResult<>(list, p.getTotal(), (int) p.getCurrent(), (int) p.getSize());
    }

    // ------------------------------------------------------------------ 内部

    private void takeDown(String targetType, Long targetId, String reason) {
        if (targetType == null || targetId == null) {
            return;
        }
        switch (targetType.toUpperCase()) {
            case "POST" -> {
                Post post = postMapper.selectById(targetId);
                if (post != null) {
                    post.setStatus("OFFLINE");
                    post.setUpdatedAt(LocalDateTime.now());
                    postMapper.updateById(post);
                }
            }
            case "TASK" -> {
                Task task = taskMapper.selectById(targetId);
                if (task != null && !isFinal(task.getStatus())) {
                    if (Boolean.TRUE.equals(holdsFrozen(task.getStatus()))) {
                        walletService.unfreeze(task.getPublisherId(), task.getReward(),
                                com.baolong.wsw.enums.BizType.ADMIN, task.getId(),
                                "内容下架退回：" + task.getTitle(), "TAKEDOWN", task.getId());
                    }
                    taskMapper.adminTakeDown(task.getId(), reason == null ? "内容下架" : reason);
                }
            }
            default -> log.debug("暂不支持下架类型: {}", targetType);
        }
    }

    private Long resolveAuthor(Report report) {
        if (report.getPostId() != null) {
            Post post = postMapper.selectById(report.getPostId());
            return post == null ? null : post.getAuthorId();
        }
        if (report.getTaskId() != null) {
            Task task = taskMapper.selectById(report.getTaskId());
            return task == null ? null : task.getPublisherId();
        }
        return null;
    }

    private static boolean isFinal(String status) {
        TaskStatus value = TaskStatus.of(status);
        return value != null && value.isFinal();
    }

    private static boolean holdsFrozen(String status) {
        TaskStatus value = TaskStatus.of(status);
        return value != null && value.holdsFrozenMoney();
    }

    private long countUserStatus(UserStatus status) {
        return userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getStatus, status.name()));
    }

    private long countTaskStatus(TaskStatus status) {
        return taskMapper.selectCount(
                Wrappers.<Task>lambdaQuery().eq(Task::getStatus, status.name()));
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private static boolean isSuperAdmin(User user) {
        return user != null && "SADMIN".equals(user.getRole());
    }

    private static void accumulate(Map<LocalDate, long[]> buckets, LocalDateTime time, int index) {
        if (time == null) {
            return;
        }
        long[] counts = buckets.get(time.toLocalDate());
        if (counts != null) {
            counts[index]++;
        }
    }

    private void opLog(Long adminId, String action, String target, String detail) {
        try {
            AdminOpLog record = new AdminOpLog();
            record.setAdminId(adminId);
            record.setAction(action);
            record.setTarget(target);
            record.setDetail(detail);
            record.setCreatedAt(LocalDateTime.now());
            adminOpLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("操作日志写入失败", e);
        }
    }
}
