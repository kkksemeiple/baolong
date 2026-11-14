package com.baolong.wsw.algorithm;

import com.baolong.wsw.enums.CreditLevel;

/**
 * 信誉评分（CreditCalculator）。
 * <p>五项指标来自近 90 天的任务记录：完成率、准时率、评价、争议率、取消率。
 * 综合分 0~120，映射成五级。
 *
 * <p><b>先验平滑</b>：新用户样本太少，直接算会大起大落（一单差评就崩到底）。
 * 所以有效单量 &lt; 5 时全部用先验值（完成率 90%、准时率 90%、争议率 5%、取消率 5%、评价 4.5）；
 * 5~20 单之间线性过渡；到 20 单后完全采用真实数据。
 *
 * <p><b>发布人责任导致的取消不进分母</b>——这是本算法最容易被忽略但最影响公平性的一条：
 * 委托方自己撤单，不应该算在接单方头上。
 */
public final class CreditCalculator {

    public static final double PRIOR_FINISH_RATE = 0.90;
    public static final double PRIOR_ON_TIME_RATE = 0.90;
    public static final double PRIOR_DISPUTE_RATE = 0.05;
    public static final double PRIOR_CANCEL_RATE = 0.05;
    public static final double PRIOR_REVIEW = 4.5;

    /** 低于这个单量：完全用先验 */
    public static final int MIN_SAMPLES = 5;
    /** 达到这个单量：完全用真实数据 */
    public static final int FULL_SAMPLES = 20;

    public static final double MAX_SCORE = 120.0;

    /** 完成率、准时率、评价、争议、取消 的权重 */
    public static final double[] WEIGHTS = {0.30, 0.25, 0.25, 0.10, 0.10};

    private CreditCalculator() {
    }

    /**
     * 原始统计量。
     *
     * @param totalClaims      进入统计窗口的有效单量（已排除发布人责任的取消）
     * @param finishedClaims   完成单量
     * @param onTimeClaims     准时完成单量
     * @param disputeCount     争议单量
     * @param cancelCount      接单方责任的取消单量
     * @param reviewAvg        平均评价（1~5）
     */
    public record Metrics(int totalClaims, int finishedClaims, int onTimeClaims,
                          int disputeCount, int cancelCount, double reviewAvg) {
    }

    /**
     * 计算结果。
     */
    public record Result(double finishRate, double onTimeRate, double reviewScore,
                         double disputeRate, double cancelRate,
                         double score, CreditLevel level) {
    }

    public static Result compute(Metrics m) {
        int n = Math.max(0, m.totalClaims());

        double rawFinish = n == 0 ? PRIOR_FINISH_RATE : (double) m.finishedClaims() / n;
        double rawOnTime = m.finishedClaims() == 0
                ? PRIOR_ON_TIME_RATE
                : (double) m.onTimeClaims() / m.finishedClaims();
        double rawDispute = n == 0 ? PRIOR_DISPUTE_RATE : (double) m.disputeCount() / n;
        double rawCancel = n == 0 ? PRIOR_CANCEL_RATE : (double) m.cancelCount() / n;
        double rawReview = m.reviewAvg() <= 0 ? PRIOR_REVIEW : m.reviewAvg();

        // 真实数据权重：0 → 全先验；1 → 全真实
        double alpha = 0;
        if (n >= FULL_SAMPLES) {
            alpha = 1;
        } else if (n > MIN_SAMPLES) {
            alpha = (double) (n - MIN_SAMPLES) / (FULL_SAMPLES - MIN_SAMPLES);
        }

        double finish = blend(rawFinish, PRIOR_FINISH_RATE, alpha);
        double onTime = blend(rawOnTime, PRIOR_ON_TIME_RATE, alpha);
        double dispute = blend(rawDispute, PRIOR_DISPUTE_RATE, alpha);
        double cancel = blend(rawCancel, PRIOR_CANCEL_RATE, alpha);
        double review = blend(rawReview, PRIOR_REVIEW, alpha);

        double normalizedReview = clamp(review / 5.0);
        double score = MAX_SCORE * (WEIGHTS[0] * finish
                + WEIGHTS[1] * onTime
                + WEIGHTS[2] * normalizedReview
                + WEIGHTS[3] * (1 - dispute)
                + WEIGHTS[4] * (1 - cancel));
        score = round1(clampScore(score));

        return new Result(round4(finish), round4(onTime), round2(review),
                round4(dispute), round4(cancel), score, CreditLevel.of(score));
    }

    private static double blend(double real, double prior, double alpha) {
        return alpha * real + (1 - alpha) * prior;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double clampScore(double v) {
        return Math.max(0, Math.min(MAX_SCORE, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
