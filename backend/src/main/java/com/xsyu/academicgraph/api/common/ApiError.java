package com.xsyu.academicgraph.api.common;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一错误响应体（RFC 7807 Problem Details 风格 + 工程规范扩展）。
 * 内容类型为 application/problem+json，字段含义：
 *   status      HTTP 状态码（与响应码一致）
 *   title       错误的简短人话描述
 *   detail      详细说明（给开发/排查看）
 *   instance    出错请求的路径
 *   errorCode   机器可读的错误编码，前端可据此做分支处理
 *   traceId     32 位十六进制链路 ID，与响应头 X-Trace-Id 一致，查日志用
 *   fieldErrors 参数校验失败时逐字段列出（字段名 + 中文提示）
 *   timestamp   出错时间
 */
public record ApiError(
        int status,
        String title,
        String detail,
        String instance,
        String errorCode,
        String traceId,
        List<FieldError> fieldErrors,
        LocalDateTime timestamp
) {
    /** 单个字段的校验错误 */
    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String title, String detail, String instance,
                              String errorCode, String traceId) {
        return new ApiError(status, title, detail, instance, errorCode, traceId, null, LocalDateTime.now());
    }

    public static ApiError of(int status, String title, String detail, String instance,
                              String errorCode, String traceId, List<FieldError> fieldErrors) {
        return new ApiError(status, title, detail, instance, errorCode, traceId, fieldErrors, LocalDateTime.now());
    }
}
