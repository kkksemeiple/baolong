package com.baolong.wsw.service;

import com.baolong.wsw.entity.AuditItem;
import com.baolong.wsw.mapper.AuditItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审核入队：把命中敏感词或被人举报的内容写进 {@code audit_item}。
 *
 * <p>单独抽出来是为了避免服务之间互相注入成环：
 * {@code TaskService}、{@code ForumService}、{@code InteractionService} 都要往队列里塞东西，
 * 谁都不需要知道别人的业务逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditItemMapper auditItemMapper;

    /**
     * 入队一条待审内容。
     *
     * <p>用 {@code REQUIRES_NEW}：即使外层业务（发布任务）后面回滚了，
     * 审核记录也要留下——这是风控数据，不属于业务事务。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void enqueue(String targetType, Long targetId, String title,
                        String content, Long authorId, String hitReason) {
        try {
            AuditItem item = new AuditItem();
            item.setTargetType(targetType);
            item.setTargetId(targetId);
            item.setTitle(title == null ? "" : truncate(title, 120));
            item.setContent(content == null ? "" : truncate(content, 2000));
            item.setHitReason(hitReason == null ? "" : truncate(hitReason, 200));
            item.setAuthorId(authorId);
            item.setStatus("PENDING");
            item.setCreatedAt(LocalDateTime.now());
            auditItemMapper.insert(item);
        } catch (Exception e) {
            // 审核入队失败绝不能影响用户发布
            log.warn("审核入队失败: type={}, id={}", targetType, targetId, e);
        }
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
