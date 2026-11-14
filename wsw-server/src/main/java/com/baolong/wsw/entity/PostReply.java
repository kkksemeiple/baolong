package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回帖，支持二级回复（parentId）。
 */
@Data
@TableName("post_reply")
public class PostReply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    /** 二级回复指向的父回复 */
    private Long parentId;

    private String content;

    private Long authorId;

    private String status;

    private LocalDateTime createdAt;
}
