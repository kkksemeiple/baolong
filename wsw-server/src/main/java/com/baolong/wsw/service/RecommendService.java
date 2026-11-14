package com.baolong.wsw.service;

import com.baolong.wsw.algorithm.DiversityReranker;
import com.baolong.wsw.algorithm.TaskScorer;
import com.baolong.wsw.dto.TaskDto;
import com.baolong.wsw.entity.Task;
import com.baolong.wsw.mapper.RecommendMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个性化推荐服务。
 *
 * <p>流程严格按设计文档走：
 * <ol>
 *   <li>取候选池（待接单 / 未过期 / 无承接人，上限 200）</li>
 *   <li>用 {@link TaskScorer} 给每条任务打 6 因子加权分</li>
 *   <li>用 {@link DiversityReranker} 做多样性重排（同发布者 ≤2、同类型 ≤3）</li>
 * </ol>
 *
 * <p>用户历史行为不足 3 次时走冷启动公式，不假装「懂你」。
 * 只给任务大厅做重排序，不额外造一个独立的推荐页面。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private final RecommendMapper recommendMapper;
    private final TaskService taskService;
    private final CreditService creditService;

    /**
     * 推荐任务列表。
     *
     * @param userId     当前用户（游客为 null，直接走冷启动画像）
     * @param typeFilter 可选：只看某类任务
     * @param size       返回条数
     */
    public List<TaskDto.TaskVO> recommend(Long userId, String typeFilter, int size) {
        List<Task> pool = recommendMapper.candidatePool();
        if (pool.isEmpty()) {
            return List.of();
        }
        TaskScorer.BehaviorProfile profile = profileOf(userId);
        LocalDateTime now = LocalDateTime.now();

        Map<Long, Double> creditCache = new HashMap<>();
        Map<Long, Integer> doneCache = new HashMap<>();
        List<Scored> scored = new ArrayList<>(pool.size());

        for (Task task : pool) {
            if (typeFilter != null && !typeFilter.isBlank()
                    && !typeFilter.equalsIgnoreCase(task.getType())) {
                continue;
            }
            // 不给自己发布的单做推荐
            if (userId != null && userId.equals(task.getPublisherId())) {
                continue;
            }
            Long publisherId = task.getPublisherId();
            double credit = creditCache.computeIfAbsent(publisherId, creditService::scoreOf);
            int done = doneCache.computeIfAbsent(publisherId, recommendMapper::publisherDone);
            TaskScorer.Features features = new TaskScorer.Features(
                    task.getType(),
                    task.getReward() == null ? 0L : task.getReward(),
                    credit,
                    task.getCreatedAt(),
                    task.getDeadline(),
                    done);
            scored.add(new Scored(task, TaskScorer.total(features, profile, now)));
        }

        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<Task> sorted = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            sorted.add(s.task());
        }
        List<Task> diverse = DiversityReranker.rerank(sorted, Task::getPublisherId, Task::getType);

        int limit = Math.max(1, Math.min(size, 50));
        if (diverse.size() > limit) {
            diverse = diverse.subList(0, limit);
        }
        log.debug("推荐完成: user={}, 候选={}, 命中={}", userId, pool.size(), diverse.size());
        return taskService.toVOs(diverse, userId);
    }

    /**
     * 构建行为画像。
     * <p>接取 +3、收藏 +2，都按 {@code 0.5^(距今天数/30)} 做时间衰减后累加，
     * 再除以最高分归一化到 [0,1]；没出现过的类型由 {@link TaskScorer#typeScore} 兜底为 0.1。
     */
    private TaskScorer.BehaviorProfile profileOf(Long userId) {
        if (userId == null) {
            return new TaskScorer.BehaviorProfile(Map.of(), 0, 0);
        }
        int history = recommendMapper.historyCount(userId);
        Map<String, Double> raw = new HashMap<>();
        accumulate(raw, recommendMapper.claimHistory(userId), 3.0);
        accumulate(raw, recommendMapper.favoriteHistory(userId), 2.0);

        Map<String, Double> affinity = new HashMap<>();
        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (max > 0) {
            for (Map.Entry<String, Double> entry : raw.entrySet()) {
                affinity.put(entry.getKey(), Math.min(1.0, entry.getValue() / max));
            }
        }
        double avgReward = recommendMapper.avgReward(userId);
        return new TaskScorer.BehaviorProfile(affinity, avgReward, history);
    }

    private void accumulate(Map<String, Double> raw, List<Map<String, Object>> rows, double weight) {
        if (rows == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> row : rows) {
            Object type = row.get("type");
            if (type == null) {
                continue;
            }
            long count = toLong(row.get("cnt"));
            double days = daysSince(row.get("last_at"), now);
            double weightScore = weight * Math.pow(0.5, days / 30.0) * count;
            raw.merge(String.valueOf(type), weightScore, Double::sum);
        }
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static double daysSince(Object value, LocalDateTime now) {
        LocalDateTime time = null;
        if (value instanceof LocalDateTime ldt) {
            time = ldt;
        } else if (value instanceof java.sql.Timestamp ts) {
            time = ts.toLocalDateTime();
        }
        if (time == null) {
            return 0;
        }
        return Math.max(0, Duration.between(time, now).toMinutes() / 1440.0);
    }

    /** 打分中间态 */
    private record Scored(Task task, double score) {
    }
}
