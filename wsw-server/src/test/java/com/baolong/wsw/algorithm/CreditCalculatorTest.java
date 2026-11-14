package com.baolong.wsw.algorithm;

import com.baolong.wsw.enums.CreditLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 白盒测试③：信誉评分的先验平滑与等级映射。
 *
 * <p>这里最容易写错的是「样本量不同时期的权重」——新用户全用先验、
 * 20 单后全用真实数据、中间线性过渡。写反了会让新人一上来就被判低分，
 * 或者让刷单的人很容易刷到满分。
 */
@DisplayName("白盒-信誉评分：CreditCalculator")
class CreditCalculatorTest {

    /** 全先验的理论分：120 × 0.91 = 109.2 */
    private static final double PRIOR_SCORE = 109.2;

    @Test
    @DisplayName("零单新用户：完全使用先验，得分 109.2")
    void newUserUsesPrior() {
        CreditCalculator.Result result = CreditCalculator.compute(
                new CreditCalculator.Metrics(0, 0, 0, 0, 0, 0));
        assertEquals(PRIOR_SCORE, result.score(), 1e-9);
        assertEquals(CreditLevel.GOOD, result.level());
    }

    @Test
    @DisplayName("5 单以内仍走先验：坏记录被平滑掉")
    void smallSampleIsSmoothed() {
        // 1 单且未完成、有争议、差评，但样本太小，先验仍占主导
        CreditCalculator.Result result = CreditCalculator.compute(
                new CreditCalculator.Metrics(1, 0, 0, 1, 0, 1.0));
        assertEquals(PRIOR_SCORE, result.score(), 1e-9,
                "样本量 ≤ MIN_SAMPLES 时应完全使用先验");
    }

    @Test
    @DisplayName("满 20 单且全部准时、五星：满分 120")
    void perfectTraderHitsMax() {
        CreditCalculator.Result result = CreditCalculator.compute(
                new CreditCalculator.Metrics(20, 20, 20, 0, 0, 5.0));
        assertEquals(120.0, result.score(), 1e-9);
        assertEquals(CreditLevel.EXCELLENT, result.level());
    }

    @Test
    @DisplayName("20 单里一半差评加争议：掉到 FROZEN 以下")
    void badTraderFallsBelowLimit() {
        CreditCalculator.Result result = CreditCalculator.compute(
                new CreditCalculator.Metrics(20, 10, 5, 8, 8, 2.0));
        assertTrue(result.score() < 60, "应低于 60，实际 " + result.score());
        assertEquals(CreditLevel.FROZEN, result.level());
        assertTrue(!result.level().canAccept(), "冻结等级不能接单");
    }

    @Test
    @DisplayName("10 单时真实数据权重 1/3，得分 112.8")
    void midSampleBlendsLinearly() {
        CreditCalculator.Result result = CreditCalculator.compute(
                new CreditCalculator.Metrics(10, 10, 10, 0, 0, 5.0));
        assertEquals(112.8, result.score(), 1e-9);
        assertEquals(CreditLevel.EXCELLENT, result.level());
    }

    @Test
    @DisplayName("分数随完成率单调上升")
    void scoreIsMonotonicInFinishRate() {
        double low = CreditCalculator.compute(
                new CreditCalculator.Metrics(20, 10, 10, 0, 0, 5.0)).score();
        double high = CreditCalculator.compute(
                new CreditCalculator.Metrics(20, 20, 20, 0, 0, 5.0)).score();
        assertTrue(high > low, "完成率高的应得分更高：" + high + " vs " + low);
    }

    @Test
    @DisplayName("等级阈值：110/95/80/60 四条线")
    void levelThresholds() {
        assertEquals(CreditLevel.EXCELLENT, CreditLevel.of(115));
        assertEquals(CreditLevel.EXCELLENT, CreditLevel.of(110));
        assertEquals(CreditLevel.GOOD, CreditLevel.of(109.9));
        assertEquals(CreditLevel.GOOD, CreditLevel.of(95));
        assertEquals(CreditLevel.NORMAL, CreditLevel.of(94.9));
        assertEquals(CreditLevel.NORMAL, CreditLevel.of(80));
        assertEquals(CreditLevel.LIMITED, CreditLevel.of(79.9));
        assertEquals(CreditLevel.LIMITED, CreditLevel.of(60));
        assertEquals(CreditLevel.FROZEN, CreditLevel.of(59.9));
    }
}
