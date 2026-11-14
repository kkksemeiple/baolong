package com.baolong.wsw.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务模块的入参与出参。
 * <p>用 record 而不是一堆 getter/setter 类，是为了让「接口长什么样」一眼能看完。
 * <p>注意：出参里的 {@code status} 走的是前端既有契约（OPEN/TAKEN/SUBMITTED…），
 * 与设计文档的 WAITING/IN_PROGRESS/TO_CONFIRM 一一对应，转换见 {@code TaskWire}。
 */
public final class TaskDto {

    private TaskDto() {
    }

    // ---------------------------------------------------------------- 出参

    /** 用户摘要（列表里避免传整个用户对象） */
    public record UserBrief(Long id, String nickname, String avatar, Integer creditScore) {
    }

    /** 任务卡片 / 详情共用的出参 */
    public record TaskVO(
            Long id,
            String title,
            String description,
            String type,
            String typeLabel,
            Long reward,
            LocalDateTime deadline,
            LocalDateTime startTime,
            Integer durationMinutes,
            String location,
            Integer peopleNeeded,
            List<String> images,
            String status,
            String statusLabel,
            Long publisherId,
            UserBrief publisher,
            Long takerId,
            UserBrief taker,
            Boolean favorited,
            Boolean mine,
            LocalDateTime createdAt,
            LocalDateTime submittedAt,
            LocalDateTime confirmedAt,
            List<String> tags,
            Integer viewCount,
            Integer favorCount,
            /** 接单方提交的交付说明（待确认/已完成时才有值，用于发布方核对） */
            String submitContent,
            /** 接单方上传的交付凭证图片 URL 列表 */
            List<String> submitImages
    ) {
    }

    /** 接单记录出参 */
    public record ClaimVO(
            Long id,
            Long taskId,
            Long claimerId,
            String status,
            String statusLabel,
            LocalDateTime claimedAt,
            LocalDateTime submittedAt,
            LocalDateTime confirmedAt,
            Integer rating,
            String reviewContent,
            String submitContent,
            List<String> submitProof
    ) {
    }

    /** 议价记录出参 */
    public record BargainVO(
            Long id,
            Long taskId,
            Long fromUserId,
            Long toUserId,
            Long amount,
            String status,
            String statusLabel,
            String message,
            LocalDateTime createdAt
    ) {
    }

    // ---------------------------------------------------------------- 入参

    /** 发布任务 */
    public record CreateReq(
            @NotBlank(message = "标题不能为空")
            @Size(max = 60, message = "标题最多 60 字")
            String title,

            @NotBlank(message = "描述不能为空")
            @Size(max = 2000, message = "描述最多 2000 字")
            String description,

            @NotBlank(message = "请选择任务类型")
            String type,

            @NotNull(message = "报酬不能为空")
            @DecimalMin(value = "1", message = "报酬至少 1 屋币")
            Long reward,

            @NotNull(message = "截止时间不能为空")
            LocalDateTime deadline,

            String location,

            Integer peopleNeeded,

            List<String> images,

            /** 期望开始时间，缺省取截止时间前 2 小时 */
            LocalDateTime startTime,

            /** 预计时长（分钟），缺省 60 */
            Integer durationMinutes,

            List<String> tags
    ) {
    }

    /** 修改任务（仅 WAITING 状态可改） */
    public record UpdateReq(
            @Size(max = 60, message = "标题最多 60 字") String title,
            @Size(max = 2000, message = "描述最多 2000 字") String description,
            String type,
            Long reward,
            LocalDateTime deadline,
            String location,
            Integer peopleNeeded,
            List<String> images,
            LocalDateTime startTime,
            Integer durationMinutes,
            List<String> tags
    ) {
    }

    /** 接单方提交完成 */
    public record SubmitReq(
            @NotBlank(message = "请填写完成说明") String content,
            List<String> images
    ) {
    }

    /** 委托方确认 / 拒绝 */
    public record ConfirmReq(
            /** 1~5 星，可空 */
            Integer rating,
            String content
    ) {
    }

    /** 取消 / 放弃 */
    public record CancelReq(String reason) {
    }

    /** 发起争议 */
    public record DisputeReq(
            @NotBlank(message = "请填写争议原因") String reason,
            List<String> evidence
    ) {
    }

    /** 议价 */
    public record BargainReq(
            @NotNull(message = "请填写报价") @DecimalMin(value = "1", message = "报价至少 1 屋币") Long amount,
            String message
    ) {
    }

    /** 大厅 / 搜索的查询条件（对应设计文档的 SearchCondition） */
    public record TaskQuery(
            String keyword,
            String type,
            String status,
            Long minReward,
            Long maxReward,
            /** latest / reward / deadline / recommend */
            String sort,
            Long cursor,
            Integer size
    ) {
    }
}
