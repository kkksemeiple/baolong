package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 浏览历史。
 * <p>同一个人重复看同一个任务只更新查看时间，不会刷出一堆重复记录——
 * 靠 {@code uk_user_target} 唯一索引 + {@code ON DUPLICATE KEY UPDATE} 实现。
 * 同时它也是推荐算法里「行为数据」的来源之一。
 */
@Data
@TableName("browse_history")
public class BrowseHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String targetType;

    private Long targetId;

    private LocalDateTime viewedAt;
}
