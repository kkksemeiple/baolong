package com.baolong.wsw.service;

import com.baolong.wsw.common.BizException;
import com.baolong.wsw.common.CursorPage;
import com.baolong.wsw.dto.MessageDto;
import com.baolong.wsw.dto.TaskDto;
import com.baolong.wsw.entity.Conversation;
import com.baolong.wsw.entity.Message;
import com.baolong.wsw.entity.Task;
import com.baolong.wsw.entity.User;
import com.baolong.wsw.entity.UserCredit;
import com.baolong.wsw.enums.MsgType;
import com.baolong.wsw.mapper.ConversationMapper;
import com.baolong.wsw.mapper.MessageMapper;
import com.baolong.wsw.mapper.TaskMapper;
import com.baolong.wsw.mapper.UserCreditMapper;
import com.baolong.wsw.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 私信服务。
 *
 * <p>会话的唯一性靠「{@code user_a} 恒为较小 id」保证：同一对用户围绕同一任务只会有一个会话，
 * 谁先发起都一样。发消息不需要 WebSocket——前端轮询即可，课程项目不做长连接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final UserCreditMapper userCreditMapper;

    // ------------------------------------------------------------------ 会话

    /** 我的会话列表（按最近消息倒序） */
    public List<MessageDto.ConversationVO> conversations(Long userId) {
        List<Conversation> rows = conversationMapper.selectList(
                Wrappers.<Conversation>lambdaQuery()
                        .and(w -> w.eq(Conversation::getUserA, userId).or().eq(Conversation::getUserB, userId))
                        .orderByDesc(Conversation::getLastMessageAt)
                        .last("LIMIT 100"));
        List<MessageDto.ConversationVO> result = new ArrayList<>(rows.size());
        for (Conversation conversation : rows) {
            result.add(toVO(conversation, userId));
        }
        return result;
    }

    /**
     * 取（或创建）我与对方的会话。
     *
     * <p>会话按「人」唯一：同一对用户只有一个会话，不按任务拆分。
     * 之前按 (任务, 双方) 建，接过同一人的多个任务就会出现一串重复会话。
     * 任务只作为「最近一次相关的任务」留痕（task_id），列表不再展示。
     */
    @Transactional(rollbackFor = Exception.class)
    public MessageDto.ConversationVO byTask(Long userId, Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        // 只有任务的双方能拉会话。否则任何一个登录用户只要知道任务 id，
        // 就能和任意发布人建立会话并私聊 —— 这是骚扰与垃圾信息的入口。
        boolean party = task.getPublisherId().equals(userId)
                || (task.getClaimantId() != null && task.getClaimantId().equals(userId));
        if (!party) {
            throw BizException.forbidden("只有任务相关方才能发起会话");
        }
        Long peerId = task.getPublisherId().equals(userId) ? task.getClaimantId() : task.getPublisherId();
        if (peerId == null) {
            throw BizException.badRequest("任务还没有接单方，暂时无法聊天");
        }
        if (peerId.equals(userId)) {
            throw BizException.badRequest("不能和自己聊天");
        }
        Conversation conversation = findOrCreate(userId, peerId, taskId);
        return toVO(conversation, userId);
    }

    public MessageDto.ConversationVO detail(Long userId, Long conversationId) {
        return toVO(requireConversation(userId, conversationId), userId);
    }

    /**
     * 接单成功后自动建会话。
     * <p>否则用户会疑惑「接了单为什么消息里什么都没有」——
     * 之前只有点「联系对方」才建会话，等于把建会话这件事推给了用户。
     * 幂等：已存在就什么都不做。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureForTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getClaimantId() == null || task.getPublisherId() == null) {
            return;
        }
        Long publisherId = task.getPublisherId();
        Long claimantId = task.getClaimantId();
        if (publisherId.equals(claimantId)) {
            return;
        }
        findOrCreate(publisherId, claimantId, taskId);
    }

    /**
     * 按「一对用户」查会话，没有就建；有则只更新 task_id（最近一次相关的任务）。
     * 唯一约束是 (user_a, user_b)，所以同一对用户永远只有一条会话。
     */
    private Conversation findOrCreate(Long userAId, Long userBId, Long taskId) {
        Conversation conversation = find(userAId, userBId);
        if (conversation != null) {
            if (taskId != null && !taskId.equals(conversation.getTaskId())) {
                conversation.setTaskId(taskId);
                conversationMapper.updateById(conversation);
            }
            return conversation;
        }
        conversation = new Conversation();
        conversation.setTaskId(taskId);
        conversation.setUserA(Math.min(userAId, userBId));
        conversation.setUserB(Math.max(userAId, userBId));
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    // ------------------------------------------------------------------ 消息

    /**
     * 会话消息。
     *
     * <p>返回顺序统一是 <b>由旧到新</b>（前端直接从上往下渲染即可）。
     * 早先按 id 倒序返回，聊天窗口就出现「新消息跑到旧消息上面」。
     *
     * @param after 增量拉取：只要 id 大于它的消息，用于前端轮询收新消息
     */
    public CursorPage<MessageDto.MessageVO> messages(Long userId, Long conversationId,
                                                     Long cursor, Integer size, Long after) {
        requireConversation(userId, conversationId);

        // 增量模式：拿 after 之后的新消息，正序，不做翻页
        if (after != null) {
            List<Message> fresh = messageMapper.selectList(
                    Wrappers.<Message>lambdaQuery()
                            .eq(Message::getConversationId, conversationId)
                            .gt(Message::getId, after)
                            .orderByAsc(Message::getId)
                            .last("LIMIT 50"));
            List<MessageDto.MessageVO> freshList = new ArrayList<>(fresh.size());
            for (Message message : fresh) {
                freshList.add(toVO(message, userId));
            }
            return CursorPage.of(freshList, null);
        }

        int limit = Math.max(1, Math.min(size == null ? 20 : size, 50));
        // 先按倒序取「最近的一页」，再翻转成正序输出
        List<Message> rows = messageMapper.selectList(
                Wrappers.<Message>lambdaQuery()
                        .eq(Message::getConversationId, conversationId)
                        .lt(cursor != null, Message::getId, cursor)
                        .orderByDesc(Message::getId)
                        .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        // 此时 rows 是最新在前，翻页游标要指向本页最旧的一条
        String next = hasMore && !rows.isEmpty()
                ? String.valueOf(rows.get(rows.size() - 1).getId())
                : null;
        List<Message> ordered = new ArrayList<>(rows);
        java.util.Collections.reverse(ordered);

        List<MessageDto.MessageVO> list = new ArrayList<>(ordered.size());
        for (Message message : ordered) {
            list.add(toVO(message, userId));
        }
        return CursorPage.of(list, next);
    }

    @Transactional(rollbackFor = Exception.class)
    public MessageDto.MessageVO send(Long userId, Long conversationId, MessageDto.SendReq req) {
        Conversation conversation = requireConversation(userId, conversationId);
        Long toUserId = conversation.getUserA().equals(userId) ? conversation.getUserB() : conversation.getUserA();

        String contentType = req.contentType() == null ? MsgType.TEXT.name() : req.contentType().toUpperCase();
        if (!MsgType.TEXT.name().equals(contentType) && !MsgType.IMAGE.name().equals(contentType)) {
            contentType = MsgType.TEXT.name();
        }

        Message message = new Message();
        message.setConversationId(conversationId);
        message.setFromUserId(userId);
        message.setToUserId(toUserId);
        message.setContent(req.content());
        message.setContentType(contentType);
        message.setReadFlag(false);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);

        conversation.setLastMessage(MsgType.IMAGE.name().equals(contentType) ? "[图片]" : req.content());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);

        return toVO(message, userId);
    }

    /** 把该会话里发给我的消息全部标为已读 */
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long conversationId) {
        requireConversation(userId, conversationId);
        messageMapper.markRead(conversationId, userId);
    }

    // ------------------------------------------------------------------ 内部

    private Conversation find(Long userA, Long userB) {
        return conversationMapper.selectOne(
                Wrappers.<Conversation>lambdaQuery()
                        .eq(Conversation::getUserA, Math.min(userA, userB))
                        .eq(Conversation::getUserB, Math.max(userA, userB))
                        .last("LIMIT 1"));
    }

    private Conversation requireConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw BizException.notFound("会话不存在");
        }
        if (!conversation.getUserA().equals(userId) && !conversation.getUserB().equals(userId)) {
            throw BizException.forbidden("无权访问该会话");
        }
        return conversation;
    }

    private MessageDto.ConversationVO toVO(Conversation conversation, Long userId) {
        Long peerId = conversation.getUserA().equals(userId) ? conversation.getUserB() : conversation.getUserA();
        User peer = userMapper.selectById(peerId);
        String taskTitle = null;
        if (conversation.getTaskId() != null) {
            Task task = taskMapper.selectById(conversation.getTaskId());
            taskTitle = task == null ? null : task.getTitle();
        }
        Long unread = messageMapper.selectCount(
                Wrappers.<Message>lambdaQuery()
                        .eq(Message::getConversationId, conversation.getId())
                        .eq(Message::getToUserId, userId)
                        .eq(Message::getReadFlag, false));
        TaskDto.UserBrief peerBrief = null;
        if (peer != null) {
            UserCredit credit = userCreditMapper.selectById(peerId);
            int score = credit == null || credit.getCreditScore() == null
                    ? 100 : (int) Math.round(credit.getCreditScore().doubleValue());
            peerBrief = new TaskDto.UserBrief(peer.getId(), peer.getNickname(),
                    peer.getAvatar(), score);
        }
        return new MessageDto.ConversationVO(
                conversation.getId(),
                conversation.getTaskId(),
                taskTitle,
                peerBrief,
                conversation.getLastMessage(),
                conversation.getLastMessageAt(),
                unread == null ? 0 : unread);
    }

    private MessageDto.MessageVO toVO(Message message, Long userId) {
        // 发送者的昵称/头像随消息一起下发：前端不再依赖本地缓存的用户信息，
        // 即使同一浏览器的另一个标签页把 localStorage 覆盖了，气泡上的名字也不会错。
        User from = userMapper.selectById(message.getFromUserId());
        return new MessageDto.MessageVO(
                message.getId(),
                message.getConversationId(),
                message.getFromUserId(),
                message.getToUserId(),
                from == null ? null : from.getNickname(),
                from == null ? null : from.getAvatar(),
                message.getContent(),
                message.getContentType(),
                message.getFromUserId().equals(userId),
                message.getReadFlag(),
                message.getCreatedAt());
    }
}
