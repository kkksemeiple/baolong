package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话。两个用户围绕一个任务聊天。
 * <p>{@code user_a} 永远是 id 较小的一方，保证同一对用户只产生一条会话。
 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long userA;

    private Long userB;

    private String lastMessage;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;
}
