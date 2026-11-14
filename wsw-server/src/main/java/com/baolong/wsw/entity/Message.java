package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私信。
 */
@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long fromUserId;

    private Long toUserId;

    private String content;

    /** TEXT / IMAGE */
    private String contentType;

    private Boolean readFlag;

    private LocalDateTime createdAt;
}
