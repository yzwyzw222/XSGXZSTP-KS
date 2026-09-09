package com.aacv.system.shared.domain;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public PageResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        if (page < 0 || size < 1 || size > 100 || totalElements < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        long pages = totalElements == 0 ? 0 : ((totalElements - 1) / size) + 1;
        int totalPages = pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
        return new PageResult<>(items, page, size, totalElements, totalPages);
    }
}
