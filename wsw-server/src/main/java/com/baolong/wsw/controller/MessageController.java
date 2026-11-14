package com.baolong.wsw.controller;

import com.baolong.wsw.common.CursorPage;
import com.baolong.wsw.common.Result;
import com.baolong.wsw.dto.MessageDto;
import com.baolong.wsw.entity.Notification;
import com.baolong.wsw.security.AuthContext;
import com.baolong.wsw.service.MessageService;
import com.baolong.wsw.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消息与通知接口（对应前端 {@code src/api/message.ts}）。
 */
@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ 会话

    /** 我的会话列表 */
    @GetMapping("/conversations")
    public Result<List<MessageDto.ConversationVO>> conversations() {
        return Result.ok(messageService.conversations(AuthContext.requireUserId()));
    }

    /** 按任务取会话，没有就建一个 */
    @GetMapping("/conversations/by-task/{taskId}")
    public Result<MessageDto.ConversationVO> byTask(@PathVariable Long taskId) {
        return Result.ok(messageService.byTask(AuthContext.requireUserId(), taskId));
    }

    /** 会话详情 */
    @GetMapping("/conversations/{id}")
    public Result<MessageDto.ConversationVO> conversation(@PathVariable Long id) {
        return Result.ok(messageService.detail(AuthContext.requireUserId(), id));
    }

    /**
     * 会话消息。
     * <p>{@code after} 用于前端轮询增量拉取（只要 id 比它大的新消息）。
     */
    @GetMapping("/conversations/{id}/messages")
    public Result<CursorPage<MessageDto.MessageVO>> messages(
            @PathVariable Long id,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(messageService.messages(AuthContext.requireUserId(), id, cursor, size, after));
    }

    /** 发消息 */
    @PostMapping("/conversations/{id}/messages")
    public Result<MessageDto.MessageVO> send(@PathVariable Long id,
                                             @Valid @RequestBody MessageDto.SendReq req) {
        return Result.ok(messageService.send(AuthContext.requireUserId(), id, req));
    }

    /** 会话已读 */
    @PostMapping("/conversations/{id}/read")
    public Result<Void> readConversation(@PathVariable Long id) {
        messageService.markRead(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 通知

    /** 通知列表 */
    @GetMapping("/notifications")
    public Result<CursorPage<MessageDto.NotificationVO>> notifications(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        CursorPage<Notification> page = notificationService.list(
                AuthContext.requireUserId(), type, unreadOnly, cursor, size == null ? 20 : size);
        List<MessageDto.NotificationVO> list = new ArrayList<>(page.list().size());
        for (Notification n : page.list()) {
            list.add(new MessageDto.NotificationVO(n.getId(), n.getType(), n.getTitle(), n.getContent(),
                    n.getBizId(), n.getLink(), n.getReadFlag(), n.getCreatedAt()));
        }
        return Result.ok(CursorPage.of(list, page.nextCursor()));
    }

    /** 未读通知数（顶部小红点）：前端要 total + 分类计数，一次给全 */
    @GetMapping("/notifications/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(notificationService.unreadSummary(AuthContext.requireUserId()));
    }

    /**
     * 标记已读：兼容前端的批量写法 {@code {"ids":[1,2,3]}} 与单条 {@code {"id":1}}。
     * <p>之前只收 {@code Map<String, Long>}，前端传数组会直接反序列化失败并报「请求参数不合法」。
     */
    @PostMapping("/notifications/read")
    public Result<Void> readOne(@RequestBody Map<String, Object> body) {
        Long userId = AuthContext.requireUserId();
        if (body == null) {
            return Result.ok(null);
        }
        Object ids = body.get("ids");
        if (ids instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number number) {
                    notificationService.markRead(userId, number.longValue());
                }
            }
            return Result.ok(null);
        }
        Object id = body.get("id");
        if (id instanceof Number number) {
            notificationService.markRead(userId, number.longValue());
        }
        return Result.ok(null);
    }

    /** 全部已读 */
    @PostMapping("/notifications/read-all")
    public Result<Void> readAll() {
        notificationService.markAllRead(AuthContext.requireUserId());
        return Result.ok(null);
    }
}
