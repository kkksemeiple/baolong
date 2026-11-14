package com.baolong.wsw.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 白盒测试②：推荐算法六个因子的边界值。
 *
 * <p>推荐分直接决定任务大厅的排序，任何一个因子的公式写错都会变成「乱序」，
 * 而这种错在页面上几乎看不出来。所以这里对每个因子单独钉边界，
 * 再验证整体加权与冷启动分支。
 */
@DisplayName("白盒-推荐算法：TaskScorer 六因子")
class TaskScorerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 12, 0, 0);

    @Test
    @DisplayName("信用分：60 分以下记 0，120 分记 1，中间线性")
    void creditScoreBoundary() {
        assertEquals(0.0, TaskScorer.creditScore(60), 1e-9);
        assertEquals(0.5, TaskScorer.creditScore(90), 1e-9);
        assertEquals(1.0, TaskScorer.creditScore(120), 1e-9);
        // 超界要被夹住，不能算出负数或大于 1
        assertEquals(0.0, TaskScorer.creditScore(10), 1e-9);
        assertEquals(1.0, TaskScorer.creditScore(200), 1e-9);
    }

    @Test
    @DisplayName("新鲜度：正好 72 小时衰减一半")
    void freshHalfLife() {
        assertEquals(0.5, TaskScorer.freshScore(NOW.minusHours(72), NOW), 1e-9);
        assertEquals(1.0, TaskScorer.freshScore(NOW, NOW), 1e-9);
        assertEquals(0.25, TaskScorer.freshScore(NOW.minusHours(144), NOW), 1e-9);
    }

    @Test
    @DisplayName("截止紧迫度：现在到期记 1，超过 7 天记 0")
    void dueWindow() {
        assertEquals(1.0, TaskScorer.dueScore(NOW, NOW), 1e-9);
        assertEquals(0.0, TaskScorer.dueScore(NOW.plusHours(168), NOW), 1e-9);
        assertEquals(0.0, TaskScorer.dueScore(NOW.plusDays(30), NOW), 1e-9);
        assertEquals(0.5, TaskScorer.dueScore(NOW.plusHours(84), NOW), 1e-9);
    }

    @Test
    @DisplayName("类型偏好：按最高分归一化，没见过的类型记 0.1")
    void typeAffinity() {
        Map<String, Double> affinity = Map.of("PICKUP", 10.0, "BUY", 5.0);
        assertEquals(1.0, TaskScorer.typeScore(affinity, "PICKUP"), 1e-9);
        assertEquals(0.5, TaskScorer.typeScore(affinity, "BUY"), 1e-9);
        assertEquals(TaskScorer.UNSEEN_TYPE_SCORE, TaskScorer.typeScore(affinity, "TUTOR"), 1e-9);
        // 空画像不能抛异常，也不能给 0 分（0 分会把新用户全打成同一档）
        assertEquals(TaskScorer.UNSEEN_TYPE_SCORE, TaskScorer.typeScore(Map.of(), "PICKUP"), 1e-9);
    }

    @Test
    @DisplayName("报酬贴合度：与历史均值相同记 1，偏离越远越低")
    void rewardSimilarity() {
        assertEquals(1.0, TaskScorer.rewardScore(50, 50), 1e-9);
        assertTrue(TaskScorer.rewardScore(500, 50) < 0.3);
        // 没有历史均值时给中性分 0.5，而不是 0 或 1
        assertEquals(0.5, TaskScorer.rewardScore(50, 0), 1e-9);
    }

    @Test
    @DisplayName("新用户走冷启动：不含类型偏好，报酬用线性归一")
    void coldStartFormula() {
        TaskScorer.Features features = new TaskScorer.Features(
                "PICKUP", 500, 120.0, NOW, NOW.plusHours(84), 0.0);
        TaskScorer.BehaviorProfile newUser =
                new TaskScorer.BehaviorProfile(Map.of(), 0, 0);

        assertTrue(newUser.coldStart());
        double expected = 0.3 * 1.0 + 0.3 * 1.0 + 0.3 * 1.0 + 0.05 * 0.5 + 0.05 * 0.0;
        assertEquals(expected, TaskScorer.total(features, newUser, NOW), 1e-9);
    }

    @Test
    @DisplayName("老用户按六因子权重加权，结果落在 0~1")
    void warmUserWeights() {
        TaskScorer.Features features = new TaskScorer.Features(
                "PICKUP", 50, 100.0, NOW.minusHours(24), NOW.plusHours(48), 0.8);
        TaskScorer.BehaviorProfile profile = new TaskScorer.BehaviorProfile(
                Map.of("PICKUP", 10.0), 50, 12);

        double score = TaskScorer.total(features, profile, NOW);
        assertTrue(score > 0 && score <= 1.0, "推荐分应在 0~1 之间，实际 " + score);

        // 手算复核：type=1, reward=1, credit=0.6667, fresh=2^-1/3, due=0.7143, done<1
        double expected = 0.40 * 1.0
                + 0.20 * 1.0
                + 0.15 * ((100.0 - 60) / 60)
                + 0.15 * Math.pow(0.5, 24.0 / 72)
                + 0.05 * (1 - 48.0 / 168)
                + 0.05 * Math.min(1.0, Math.log(1 + 0.8 * 21) / Math.log(21));
        assertEquals(expected, score, 1e-9);
    }

    @Test
    @DisplayName("维护者不占便宜：完成率走对数饱和，不会无限拉开差距")
    void doneRateSaturates() {
        assertEquals(0.0, TaskScorer.doneScore(0), 1e-9);
        assertTrue(TaskScorer.doneScore(1.0) <= 1.0);
        assertEquals(1.0, TaskScorer.doneScore(1.0), 1e-9);
        assertTrue(TaskScorer.doneScore(0.5) > TaskScorer.doneScore(0.2));
    }
}
