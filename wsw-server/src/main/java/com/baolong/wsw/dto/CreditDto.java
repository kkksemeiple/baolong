package com.baolong.wsw.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 信誉分明细：把「为什么是这个分」摊开给用户看。
 * <p>分数本身是黑盒算法，不解释清楚用户只会觉得「莫名其妙掉分」，
 * 所以这里返回五项比率 + 计算窗口 + 参与计算的最近接单记录。
 */
public final class CreditDto {

    private CreditDto() {
    }

    /** 参与算分的一条接单记录 */
    public record RecordVO(
            Long taskId,
            String taskTitle,
            String status,
            String statusLabel,
            Integer rating,
            LocalDateTime claimedAt,
            LocalDateTime submittedAt,
            boolean onTime
    ) {
    }

    /**
     * @param score         当前分数（0~120）
     * @param level         等级枚举名
     * @param levelLabel    等级中文名
     * @param canAccept     还能不能接单（>=80）
     * @param windowDays    统计窗口天数（默认 90）
     * @param totalClaims   窗口内有效接单量（已排除发布方责任取消）
     * @param finishedClaims 其中完成量
     */
    public record DetailVO(
            double score,
            String level,
            String levelLabel,
            double finishRate,
            double onTimeRate,
            double reviewScore,
            double disputeRate,
            double cancelRate,
            int totalClaims,
            int finishedClaims,
            int windowDays,
            boolean canAccept,
            List<RecordVO> records
    ) {
    }
}
