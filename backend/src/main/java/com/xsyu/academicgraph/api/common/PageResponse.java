package com.xsyu.academicgraph.api.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应契约（工程规范约定）：
 *   page 从 0 开始计数；size 默认 20、最大 100；
 *   totalElements 为全库命中总数，totalPages 为总页数。
 * 成功响应不带 ApiResponse 外壳，分页数据直接就是这个结构。
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    /** 把 Spring 的 Page 转成契约结构，map 参数用于实体→DTO 转换 */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
