package com.baolong.wsw.mapper;

import com.baolong.wsw.entity.BrowseHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BrowseHistoryMapper extends BaseMapper<BrowseHistory> {

    /**
     * 记一条浏览历史。
     * <p>同一个人重复看同一个任务只更新时间，不产生新行 —— 靠唯一索引 + upsert 实现，
     * 避免历史列表被同一件任务刷屏。
     */
    @Insert("""
            INSERT INTO browse_history (user_id, target_type, target_id, viewed_at)
            VALUES (#{userId}, #{targetType}, #{targetId}, NOW())
            ON DUPLICATE KEY UPDATE viewed_at = NOW()
            """)
    int upsert(@Param("userId") Long userId,
               @Param("targetType") String targetType,
               @Param("targetId") Long targetId);
}
