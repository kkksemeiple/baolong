package com.baolong.wsw.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 议价与举报的入参。
 */
public final class InteractionDto {

    private InteractionDto() {
    }

    /** 发起报价 */
    public record BargainReq(Integer amount, String message) {
    }

    /** 回应报价 */
    public record BargainRespondReq(@NotNull Long bargainId, @NotNull Boolean accept, String remark) {
    }

    /**
     * 报价记录出参。
     * <p>发布方要看清「谁、报了多少、什么时候」，只给 fromUserId 前端只能显示「用户 #3」。
     */
    public record BargainVO(
            Long id,
            Long taskId,
            Long fromUserId,
            String fromNickname,
            Long toUserId,
            Integer amount,
            String status,
            String statusLabel,
            LocalDateTime createdAt
    ) {
    }

    /** 举报 */
    public record ReportReq(String type, String reason, String evidence) {
    }

    /** 上传结果 */
    public record UploadVO(String url, String name, long size) {
    }
}
