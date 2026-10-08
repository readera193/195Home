package com.family195home.app.common;

import java.util.List;

/** 分頁查詢結果：page 從 0 起算。 */
public record PagedResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PagedResult<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PagedResult<>(items, page, size, totalElements, totalPages);
    }

    public <R> PagedResult<R> map(java.util.function.Function<T, R> mapper) {
        return new PagedResult<>(items.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
