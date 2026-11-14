package com.baolong.wsw.service;

import com.baolong.wsw.common.CursorPage;
import com.baolong.wsw.entity.Notification;
import com.baolong.wsw.enums.NotifType;
import com.baolong.wsw.mapper.NotificationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知服务。
 * <p>所有业务流程里的「通知对方」都走 {@link #push}，页面刷新拉 {@link #list}，
 * 红点数字拉 {@link #unreadCount}。通知本身不参与业务判定，写失败不应该影响主流程，
 * 所以 push 内部吞掉异常只记日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    /**
     * 推一条通知。
     *
     * @param userId  接收人
     * @param type    任务 / 资金 / 论坛 / 系统
     * @param title   一句话标题
     * @param content 正文
     * @param bizId   关联业务 id（任务 id、帖子 id 等），仅用于留痕
     * @param link    前端跳转路径，例如 {@code /tasks/12}
     */
    public void push(Long userId, NotifType type, String title, String content, Long bizId, String link) {
        if (userId == null) {
            return;
        }
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type.name());
            n.setTitle(title);
            n.setContent(content);
            n.setBizId(bizId);
            n.setLink(link);
            n.setReadFlag(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("通知写入失败: user={}, title={}", userId, title, e);
        }
    }

    public void pushTask(Long userId, String title, String content, Long taskId) {
        push(userId, NotifType.TASK, title, content, taskId, "/tasks/" + taskId);
    }

    public void pushFund(Long userId, String title, String content, Long taskId) {
        // 前端钱包路由是 /wallet（不是 /me/wallet，那是后端接口路径），
        // 写错了点开通知会直接掉进 404 页。
        push(userId, NotifType.FUND, title, content, taskId, "/wallet");
    }

    public void pushSystem(Long userId, String title, String content) {
        push(userId, NotifType.SYSTEM, title, content, null, null);
    }

    /** 通知列表（游标分页，新的在前），支持按类型与「只看未读」筛选 */
    public CursorPage<Notification> list(Long userId, Long cursor, int size) {
        return list(userId, null, false, cursor, size);
    }

    public CursorPage<Notification> list(Long userId, String type, Boolean unreadOnly, Long cursor, int size) {
        int limit = Math.max(1, Math.min(size, 50));
        boolean hasType = type != null && !type.isBlank();
        boolean onlyUnread = Boolean.TRUE.equals(unreadOnly);
        List<Notification> rows = notificationMapper.selectList(
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, userId)
                        .eq(hasType, Notification::getType, hasType ? type.trim().toUpperCase() : null)
                        .eq(onlyUnread, Notification::getReadFlag, false)
                        .lt(cursor != null, Notification::getId, cursor)
                        .orderByDesc(Notification::getId)
                        .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        String next = hasMore ? String.valueOf(rows.get(rows.size() - 1).getId()) : null;
        return CursorPage.of(rows, next);
    }

    public long unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getReadFlag, false));
        return count == null ? 0 : count;
    }

    /**
     * 顶部红点用的未读概览：total 用于红点数字，分类计数用于侧边小标签。
     * <p>同时给 count 与 total 两个键，前端历史上两个名字都用过，避免再对不上。
     */
    public java.util.Map<String, Long> unreadSummary(Long userId) {
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();
        long total = 0;
        for (NotifType type : NotifType.values()) {
            Long c = notificationMapper.selectCount(
                    Wrappers.<Notification>lambdaQuery()
                            .eq(Notification::getUserId, userId)
                            .eq(Notification::getReadFlag, false)
                            .eq(Notification::getType, type.name()));
            long n = c == null ? 0 : c;
            result.put(type.name().toLowerCase(), n);
            total += n;
        }
        result.put("total", total);
        result.put("count", total);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long id) {
        // 注意参数顺序：mapper 的签名是 markRead(id, userId)，写反了会更新 0 行且静默成功
        notificationMapper.markRead(id, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }
}
