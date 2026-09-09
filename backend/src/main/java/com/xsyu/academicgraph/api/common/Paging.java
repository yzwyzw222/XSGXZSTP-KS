package com.xsyu.academicgraph.api.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 分页参数工具：统一工程规范契约里的分页规则。
 *  - page 从 0 开始（负数按 0 处理）
 *  - size 默认 20，上限 100（防止恶意请求一次拉全表）
 *  - 默认按 id 倒序（新建的数据排前面）
 * 所有 Controller 从查询参数拿到 page/size 后都走这里做校验与修正。
 */
public final class Paging {

    private Paging() {
    }

    public static Pageable of(Integer page, Integer size) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size <= 0 ? 20 : Math.min(size, 100);
        return PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "id"));
    }
}
