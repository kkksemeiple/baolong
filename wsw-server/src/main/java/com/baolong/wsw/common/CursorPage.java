package com.baolong.wsw.common;

import java.util.List;

/**
 * 游标分页结果。
 * <p>任务大厅、资金流水、消息都用它，避免深分页（LIMIT 100000, 20）越来越慢。
 *
 * @param list       本页数据
 * @param nextCursor 下一页游标，为 null 表示没有更多
 * @param hasMore    是否还有下一页
 */
public record CursorPage<T>(List<T> list, String nextCursor, boolean hasMore) {

    public static <T> CursorPage<T> of(List<T> list, String nextCursor) {
        return new CursorPage<>(list, nextCursor, nextCursor != null);
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
