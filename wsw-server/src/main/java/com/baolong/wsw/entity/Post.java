package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论坛帖子。
 */
@Data
@TableName("post")
public class Post {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private Long authorId;

    /** JSON 数组字符串 */
    private String tags;

    private Integer replyCount;

    private Integer likeCount;

    /** NORMAL / OFFLINE / DELETED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
