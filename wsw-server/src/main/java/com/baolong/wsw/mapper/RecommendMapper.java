package com.baolong.wsw.mapper;

import com.baolong.wsw.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 推荐算法专用的只读查询。
 * <p>候选池、行为画像、发布人完成度这三类 SQL 都是「跨表聚合 + 排序截断」，
 * 用 Wrapper 拼出来反而更难读，所以集中放这里。
 */
@Mapper
public interface RecommendMapper {

    /**
     * 候选池：待接单、未过期、无承接人。
     * <p>上限 200 条是设计文档定死的，超出的任务不进入打分（避免每次请求把全表算一遍）。
     */
    @Select("""
            SELECT * FROM task
             WHERE status = 'WAITING'
               AND deadline > NOW()
               AND claimant_id IS NULL
             ORDER BY created_at DESC
             LIMIT 200
            """)
    List<Task> candidatePool();

    /** 接取行为：每类任务接了几次、最近一次是什么时候 */
    @Select("""
            SELECT t.type AS type, COUNT(*) AS cnt, MAX(c.claimed_at) AS last_at
              FROM task_claim c
              JOIN task t ON t.id = c.task_id
             WHERE c.claimer_id = #{userId}
             GROUP BY t.type
            """)
    List<Map<String, Object>> claimHistory(@Param("userId") Long userId);

    /** 收藏行为：每类任务收藏了几次、最近一次是什么时候 */
    @Select("""
            SELECT t.type AS type, COUNT(*) AS cnt, MAX(f.created_at) AS last_at
              FROM favorite f
              JOIN task t ON t.id = f.target_id
             WHERE f.user_id = #{userId}
               AND f.target_type = 'TASK'
             GROUP BY t.type
            """)
    List<Map<String, Object>> favoriteHistory(@Param("userId") Long userId);

    /** 历史接单的平均报酬，作为 reward 因子的参照中心 μ */
    @Select("""
            SELECT COALESCE(AVG(t.reward), 0)
              FROM task_claim c
              JOIN task t ON t.id = c.task_id
             WHERE c.claimer_id = #{userId}
            """)
    double avgReward(@Param("userId") Long userId);

    /** 历史行为总次数，用于判断是否冷启动 */
    @Select("SELECT COUNT(*) FROM task_claim WHERE claimer_id = #{userId}")
    int historyCount(@Param("userId") Long userId);

    /** 发布人已结算的任务数，作为 done 因子的分子 */
    @Select("SELECT COUNT(*) FROM task WHERE publisher_id = #{userId} AND status = 'SETTLED'")
    int publisherDone(@Param("userId") Long userId);
}
