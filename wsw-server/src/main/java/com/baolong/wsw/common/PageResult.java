package com.baolong.wsw.common;

import java.util.List;

/**
 * 页码分页结果（管理后台列表用）。
 */
public record PageResult<T>(List<T> list, long total, long page, long size) {

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        return new PageResult<>(list, total, page, size);
    }
}
