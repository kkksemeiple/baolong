package com.baolong.wsw.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 信誉评分专用的聚合查询。
 * <p>这些 SQL 需要 join {@code task} 才能判断「是否准时」，用 Wrapper 表达不出来，
 * 所以单独开一个只读的统计 Mapper，不污染业务 Mapper。
 */
@Mapper
public interface CreditMapper {

    /**
     * 90 天滚动窗口内的接单统计。
     * <p>「发布人责任导致的取消不计入分母」——{@code cancel_by='PUBLISHER'} 的记录整体排除，
     * 既不进 total，也不进 cancelled。
     */
    @Select("""
            SELECT COUNT(*)                                                                   AS total,
                   COALESCE(SUM(CASE WHEN c.status = 'FINISHED' THEN 1 ELSE 0 END), 0)        AS finished,
                   COALESCE(SUM(CASE WHEN c.status = 'FINISHED'
                                      AND c.submitted_at IS NOT NULL
                                      AND c.submitted_at <= t.deadline THEN 1 ELSE 0 END), 0) AS on_time,
                   COALESCE(SUM(CASE WHEN c.status = 'CANCELLED'
                                      AND c.cancel_by = 'CLAIMER' THEN 1 ELSE 0 END), 0)      AS cancelled,
                   COALESCE(AVG(c.rating), 0)                                                  AS review_avg
              FROM task_claim c
              JOIN task t ON t.id = c.task_id
             WHERE c.claimer_id = #{userId}
               AND c.claimed_at >= #{since}
               AND NOT (c.status = 'CANCELLED' AND c.cancel_by = 'PUBLISHER')
            """)
    Map<String, Object> claimStats(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /** 同一窗口内作为接单方被投诉/发起争议的次数 */
    @Select("""
            SELECT COUNT(*)
              FROM dispute d
              JOIN task t ON t.id = d.task_id
             WHERE t.claimant_id = #{userId}
               AND d.created_at >= #{since}
            """)
    int disputeCount(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * 「信誉分明细」里展示的接单记录：与算分口径完全一致（同一窗口、同一排除规则），
     * 这样用户拿列表自己加一遍，能和分数对上。
     */
    @Select("""
            SELECT c.task_id                                                              AS task_id,
                   t.title                                                                AS task_title,
                   c.status                                                               AS status,
                   c.rating                                                               AS rating,
                   c.claimed_at                                                           AS claimed_at,
                   c.submitted_at                                                         AS submitted_at,
                   CASE WHEN c.submitted_at IS NOT NULL
                             AND c.submitted_at <= t.deadline THEN 1 ELSE 0 END           AS on_time
              FROM task_claim c
              JOIN task t ON t.id = c.task_id
             WHERE c.claimer_id = #{userId}
               AND c.claimed_at >= #{since}
               AND NOT (c.status = 'CANCELLED' AND c.cancel_by = 'PUBLISHER')
             ORDER BY c.claimed_at DESC
             LIMIT #{limit}
            """)
    List<Map<String, Object>> claimRecords(@Param("userId") Long userId,
                                           @Param("since") LocalDateTime since,
                                           @Param("limit") int limit);
}
