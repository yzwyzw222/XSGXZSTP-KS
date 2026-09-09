package com.xsyu.academicgraph.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器：每个请求分配一个 32 位十六进制 traceId（UUID 去横线）。
 * 1. 写入日志上下文（MDC），出问题的日志里可以直接按 traceId 搜到整条调用链；
 * 2. 通过响应头 X-Trace-Id 返回给前端，用户报错时带着它来，排查效率翻倍。
 * @Order(HIGHEST_PRECEDENCE) 保证它跑在所有过滤器（含安全过滤器）之前。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY); // 线程池复用，必须清理，否则 traceId 会串到别的请求
        }
    }
}
