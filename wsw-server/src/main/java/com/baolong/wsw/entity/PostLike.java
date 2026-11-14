package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子点赞。
 * <p>{@code uk_post_user(post_id, user_id)} 让重复点赞直接被数据库拒绝，
 * 点赞数用 {@code set like_count = like_count + 1} 原子自增，避免丢失更新。
 */
@Data
@TableName("post_like")
public class PostLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long userId;

    private LocalDateTime createdAt;
}
