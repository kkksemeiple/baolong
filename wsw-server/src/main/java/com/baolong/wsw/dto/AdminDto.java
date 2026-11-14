package com.baolong.wsw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台的入参与出参。
 */
public final class AdminDto {

    private AdminDto() {
    }

    /** 概览卡片（字段名与前端 `AdminOverview` 严格一致） */
    public record OverviewVO(
            long userCount,
            long taskCount,
            long postCount,
            /** 任务完成率 0~100 */
            double finishRate,
            /** 纠纷率 0~100 */
            double disputeRate,
            /** 屋币累计发行（GRANT 之和） */
            long coinIssued,
            /** 当前流通量 */
            long coinCirculating,
            /** 冻结量 */
            long coinFrozen,
            /** 累计回收（违约金等） */
            long coinRecycled,
            /** 欠款总额 */
            long coinDebt
    ) {
    }

    /**
     * 趋势数据点。
     * <p>{@code date} 直接用字符串（{@code yyyy-MM-dd}），避开时区与序列化格式的坑。
     */
    public record TrendPoint(
            String date,
            long activeUsers,
            long published,
            long claimed
    ) {
    }

    /** 用户管理列表项 */
    public record AdminUserVO(
            Long id,
            String username,
            String nickname,
            /** 头像，后台列表要显示真实头像，缺了就退化成首字母 */
            String avatar,
            String phone,
            String role,
            String status,
            String authStatus,
            Integer creditScore,
            Integer finishedCount,
            /** 实名提交的姓名与学号，审核时要核对 */
            String realName,
            String studentNo,
            /** 学生证照片，审核实名时看 */
            String certImage,
            /** 发布过的任务数 / 接过的任务数 / 被别人举报的次数（任务或帖子） */
            Integer publishedCount,
            Integer claimedCount,
            Integer reportedCount,
            LocalDateTime createdAt
    ) {
    }

    /** 内容审核项：作者用嵌套摘要，前端直接渲染头像+昵称 */
    public record AuditVO(
            Long id,
            String targetType,
            Long targetId,
            String title,
            String content,
            String hitReason,
            TaskDto.UserBrief author,
            String status,
            LocalDateTime createdAt
    ) {
    }

    /** 举报项 */
    public record ReportVO(
            Long id,
            String type,
            Long taskId,
            Long postId,
            String targetTitle,
            TaskDto.UserBrief reporter,
            String reason,
            List<String> evidence,
            String status,
            String result,
            LocalDateTime createdAt
    ) {
    }

    /** 争议项 */
    public record DisputeVO(
            Long id,
            Long taskId,
            String taskTitle,
            TaskDto.UserBrief publisher,
            TaskDto.UserBrief taker,
            Long frozenAmount,
            Long initiatorId,
            TaskDto.UserBrief initiator,
            String reason,
            String evidence,
            String status,
            String result,
            String remark,
            LocalDateTime createdAt
    ) {
    }

    /** 后台任务列表项 */
    public record AdminTaskVO(
            Long id,
            String title,
            String type,
            Long reward,
            String status,
            String location,
            java.time.LocalDateTime deadline,
            Long publisherId,
            /** 发布者摘要：后台表格显示头像+昵称 */
            TaskDto.UserBrief publisher,
            Long claimantId,
            /** 接单者摘要，没人接单时是 null */
            TaskDto.UserBrief taker,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 帖子管理列表项。
     * status：NORMAL 正常 / OFFLINE 已下架 / DELETED 作者已删除
     */
    public record AdminPostVO(
            Long id,
            String title,
            String content,
            TaskDto.UserBrief author,
            java.util.List<String> tags,
            Integer replyCount,
            Integer likeCount,
            String status,
            /** 待处理举报数：>0 时列表里标红，管理员一眼看到哪条帖子被举报了 */
            Integer pendingReports,
            LocalDateTime createdAt
    ) {
    }

    /** 操作日志项 */
    public record OpLogVO(
            Long id,
            Long adminId,
            String action,
            String target,
            String detail,
            LocalDateTime createdAt
    ) {
    }

    // ------------------------------------------------------------------ 入参

    public record BanReq(String reason) {
    }

    /** 实名认证审核 */
    /**
     * 实名认证审核入参。
     * <p>同时接受 {@code approve} 与 {@code pass}：前端历史写法是 {@code {pass, reason}}，
     * 只认 {@code approve} 会导致「不能为null」——两个名字都收，谁都别再踩一次。
     */
    public record CertAuditReq(Boolean approve, Boolean pass, String reason) {
    }

    /** 内容审核 */
    public record AuditHandleReq(@NotNull Boolean approve, String reason) {
    }

    /** 举报处理：action = OFFLINE_TARGET / BAN_USER / IGNORE */
    /** 处理举报：pass=true 成立、false 不成立；result 是给双方看的处理说明 */
    public record ReportHandleReq(
            @NotNull Boolean pass,
            String result,
            /** 可选：成立时从被举报人处扣罚的屋币 */
            Long penalty
    ) {
    }

    /** 争议裁决：result = PUBLISHER / TAKER / PARTIAL / INSUFFICIENT */
    public record JudgeReq(@NotBlank String result, String remark) {
    }

    /**
     * 单个发币。
     * <p>支持两种定位方式：{@code userId} 数字 ID，或 {@code username} 登录名
     * （管理员记不住 ID，能在后台直接搜昵称/用户名更符合直觉）。
     */
    public record IssueReq(
            Long userId,
            String username,
            @NotNull Long amount,
            String reason
    ) {
    }

    /** 批量发币 */
    public record IssueBatchReq(
            @NotNull List<Long> userIds,
            @NotNull Long amount,
            String reason
    ) {
    }

    /**
     * 冲正：按「原流水 ID」反向修正，而不是让管理员凭记忆填金额。
     * <p>一条流水只能被冲正一次，重复提交会被拒绝。
     */
    public record ReverseReq(
            @NotNull Long transactionId,
            String reason
    ) {
    }

    /** 对账结果里的异常账户 */
    public record AccountAuditVO(
            Long userId,
            String username,
            /** 账面值：可用 + 冻结 − 欠款 */
            long balance,
            /** 流水推算值 */
            long calculated
    ) {
    }

    /**
     * 后台「最近流水」列表项：让管理员点一条就能拿到流水 ID，
     * 不用再去 Navicat 里查 id 抄过来。
     */
    public record RecentTransactionVO(
            Long id,
            Long userId,
            String nickname,
            String transType,
            String transLabel,
            long amount,
            String bizTitle,
            LocalDateTime createdAt
    ) {
    }

    /** 修改配置 */
    public record ConfigUpdateReq(@NotBlank String key, @NotBlank String value) {
    }
}
