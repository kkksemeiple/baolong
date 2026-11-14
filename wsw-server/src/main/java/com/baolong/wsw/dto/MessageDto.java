package com.baolong.wsw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 消息模块的入参与出参。
 */
public final class MessageDto {

    private MessageDto() {
    }

    /**
     * 会话列表项。
     * <p>{@code peer} 必须是嵌套对象，前端 {@code Conversation.peer.nickname} 直接读它；
     * 早先摊平成 peerId/peerNickname/peerAvatar，聊天窗口标题就只剩一个「?」。
     */
    public record ConversationVO(
            Long id,
            Long taskId,
            String taskTitle,
            TaskDto.UserBrief peer,
            String lastMessage,
            LocalDateTime lastMessageAt,
            Long unreadCount
    ) {
    }

    /**
     * 单条消息。
     * <p>{@code mine} 由服务端判定，前端不要再用本地缓存的用户 id 去比 ——
     * 同一个浏览器开两个标签页时 localStorage 会互相覆盖，比出来就是错的。
     */
    public record MessageVO(
            Long id,
            Long conversationId,
            Long fromUserId,
            Long toUserId,
            String fromNickname,
            String fromAvatar,
            String content,
            String contentType,
            Boolean mine,
            Boolean readFlag,
            LocalDateTime createdAt
    ) {
    }

    /** 发消息 */
    public record SendReq(
            @NotBlank(message = "消息内容不能为空")
            @Size(max = 1000, message = "单条消息最多 1000 字")
            String content,
            /** TEXT / IMAGE，为空按 TEXT 处理 */
            String contentType
    ) {
    }

    /** 通知列表项 */
    public record NotificationVO(
            Long id,
            String type,
            String title,
            String content,
            Long bizId,
            String link,
            Boolean readFlag,
            LocalDateTime createdAt
    ) {
    }
}
