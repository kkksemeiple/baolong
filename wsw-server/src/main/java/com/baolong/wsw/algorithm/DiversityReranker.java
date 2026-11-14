package com.baolong.wsw.algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 多样性重排器（DiversityReranker）。
 * <p>打分会把高分的挑上来，但结果可能是一屏全是同一个人发的小任务，
 * 或者连续十条全是「代取」——这就是刷屏。这里在打分排序之后做一次约束重排：
 * <ul>
 *   <li>前 20 条内，同一发布者最多 2 条</li>
 *   <li>前 20 条内，同一类型最多 3 条</li>
 * </ul>
 * 被挤下来的条目不会丢，而是按原顺序接在后面。
 */
public final class DiversityReranker {

    /** 约束窗口：只对前 20 条生效 */
    public static final int WINDOW = 20;
    public static final int MAX_PER_PUBLISHER = 2;
    public static final int MAX_PER_TYPE = 3;

    private DiversityReranker() {
    }

    /**
     * @param sorted       已按分数降序排列的列表
     * @param publisherOf  取发布者 ID
     * @param typeOf       取任务类型
     */
    public static <T> List<T> rerank(List<T> sorted,
                                     Function<T, Long> publisherOf,
                                     Function<T, String> typeOf) {
        if (sorted == null || sorted.size() <= 1) {
            return sorted == null ? List.of() : sorted;
        }
        List<T> result = new ArrayList<>(sorted.size());
        List<T> deferred = new ArrayList<>();
        Map<Long, Integer> publisherCount = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();

        for (T item : sorted) {
            if (result.size() >= WINDOW) {
                result.add(item);
                continue;
            }
            Long publisher = publisherOf.apply(item);
            String type = typeOf.apply(item);

            boolean publisherFull = publisherCount.getOrDefault(publisher, 0) >= MAX_PER_PUBLISHER;
            boolean typeFull = typeCount.getOrDefault(type, 0) >= MAX_PER_TYPE;
            if (publisherFull || typeFull) {
                deferred.add(item);
                continue;
            }
            result.add(item);
            publisherCount.merge(publisher, 1, Integer::sum);
            typeCount.merge(type, 1, Integer::sum);
        }
        result.addAll(deferred);
        return result;
    }
}
