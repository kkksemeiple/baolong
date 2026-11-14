package com.baolong.wsw.algorithm;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 推荐算法的打分器（TaskScorer）。
 * <p>六个因子各打 0~1 分，再按权重加权求和。所有方法都是纯函数，
 * 不依赖数据库、不依赖当前时间（时间由参数传入），因此可以被单元测试逐一覆盖。
 *
 * <pre>
 * 因子      推荐权重  默认权重   含义
 * type       0.40      0.00     任务类型偏好
 * reward     0.20      0.30     报酬是否贴合历史
 * credit     0.15      0.30     发布者信用
 * fresh      0.15      0.30     新鲜度
 * due        0.05      0.05     截止紧迫度
 * done       0.05      0.05     发布者完成率
 * </pre>
 */
public final class TaskScorer {

    public static final double[] W_RECOMMEND = {0.40, 0.20, 0.15, 0.15, 0.05, 0.05};
    public static final double[] W_DEFAULT = {0.00, 0.30, 0.30, 0.30, 0.05, 0.05};

    /** 历史行为少于 3 次视为新用户，走冷启动 */
    public static final int COLD_START_HISTORY = 3;
    /** 从未出现过的类型得分 */
    public static final double UNSEEN_TYPE_SCORE = 0.1;
    /** 报酬高斯核带宽 */
    public static final double REWARD_SIGMA = 0.8;
    /** 信用分映射区间的下界与宽度 */
    public static final double CREDIT_FLOOR = 60.0;
    public static final double CREDIT_SPAN = 60.0;
    /** 新鲜度半衰期（小时） */
    public static final double FRESH_HALF_LIFE_HOURS = 72.0;
    /** 截止紧迫度的窗口（小时）：7 天 */
    public static final double DUE_WINDOW_HOURS = 168.0;
    /** 完成率对数饱和点 */
    public static final double DONE_SATURATION = 21.0;
    /** 冷启动里报酬的参考基数 */
    public static final double COLD_REWARD_BASE = 500.0;

    private TaskScorer() {
    }

    /**
     * 任务特征（打分输入）。
     *
     * @param type              任务类型
     * @param reward            报酬
     * @param publisherCredit   发布者信用分
     * @param createdAt         发布时间
     * @param deadline          截止时间
     * @param publisherDoneRate 发布者完成率 0~1
     */
    public record Features(String type, long reward, double publisherCredit,
                           LocalDateTime createdAt, LocalDateTime deadline,
                           double publisherDoneRate) {
    }

    /**
     * 用户行为画像（打分输入）。
     *
     * @param typeAffinity 各类型的累计偏好分（已在采集时做过时间衰减）
     * @param avgReward    历史接单平均报酬
     * @param historyCount 历史行为次数（接单 + 收藏）
     */
    public record BehaviorProfile(Map<String, Double> typeAffinity,
                                  double avgReward,
                                  int historyCount) {

        public boolean coldStart() {
            return historyCount < COLD_START_HISTORY;
        }
    }

    /** 类型偏好分：除以最高分归一化，未出现过的类型记 0.1 */
    public static double typeScore(Map<String, Double> affinity, String type) {
        double max = 0;
        for (double v : affinity.values()) {
            max = Math.max(max, v);
        }
        if (max <= 0) {
            return UNSEEN_TYPE_SCORE;
        }
        Double score = affinity.get(type);
        if (score == null) {
            return UNSEEN_TYPE_SCORE;
        }
        return Math.min(1.0, score / max);
    }

    /** 报酬贴合度：与历史平均报酬越接近越高，用对数高斯核 */
    public static double rewardScore(long reward, double avgReward) {
        if (avgReward <= 0) {
            return 0.5;
        }
        double r = Math.log(reward + 1.0);
        double mu = Math.log(avgReward + 1.0);
        double diff = r - mu;
        return Math.exp(-(diff * diff) / (2 * REWARD_SIGMA * REWARD_SIGMA));
    }

    /** 发布者信用：60 分以下 0，120 分以上 1，中间线性 */
    public static double creditScore(double credit) {
        return clamp((credit - CREDIT_FLOOR) / CREDIT_SPAN);
    }

    /** 新鲜度：半衰期 72 小时的指数衰减 */
    public static double freshScore(LocalDateTime createdAt, LocalDateTime now) {
        if (createdAt == null) {
            return 0;
        }
        double hours = hoursBetween(createdAt, now);
        if (hours <= 0) {
            return 1.0;
        }
        return Math.pow(0.5, hours / FRESH_HALF_LIFE_HOURS);
    }

    /** 截止紧迫度：7 天内线性，越接近截止越高，已过期记 0 */
    public static double dueScore(LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null) {
            return 0;
        }
        double remainHours = hoursBetween(now, deadline);
        return clamp(1.0 - remainHours / DUE_WINDOW_HOURS);
    }

    /** 发布者完成率：对数饱和，防止头部发布者无限霸榜 */
    public static double doneScore(double doneRate) {
        if (doneRate <= 0) {
            return 0;
        }
        double orders = doneRate * 21.0;
        return Math.min(1.0, Math.log(1 + orders) / Math.log(DONE_SATURATION));
    }

    /**
     * 综合得分。
     * <p>新用户（历史行为 &lt; 3 次）走冷启动公式，不算类型偏好；
     * 老用户按六因子权重加权。
     */
    public static double total(Features f, BehaviorProfile profile) {
        return total(f, profile, LocalDateTime.now());
    }

    /**
     * 推荐入口：显式传入「当前时间」，便于单元测试固定时间。
     */
    public static double total(Features f, BehaviorProfile profile, LocalDateTime now) {
        double credit = creditScore(f.publisherCredit());
        double fresh = freshScore(f.createdAt(), now);
        double due = dueScore(f.deadline(), now);
        double done = doneScore(f.publisherDoneRate());

        if (profile == null || profile.coldStart()) {
            double reward = Math.min(1.0, f.reward() / COLD_REWARD_BASE);
            return 0.3 * reward + 0.3 * credit + 0.3 * fresh + 0.05 * due + 0.05 * done;
        }

        double type = typeScore(profile.typeAffinity(), f.type());
        double reward = rewardScore(f.reward(), profile.avgReward());
        double[] w = W_RECOMMEND;
        return w[0] * type + w[1] * reward + w[2] * credit
                + w[3] * fresh + w[4] * due + w[5] * done;
    }

    private static double hoursBetween(LocalDateTime from, LocalDateTime to) {
        return Duration.between(from, to).toMinutes() / 60.0;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
