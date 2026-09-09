package com.xsyu.academicgraph.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsyu.academicgraph.api.common.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * 把 ApiError 写成 application/problem+json 响应的小工具。
 * 安全过滤器的入口点/拒绝处理器发生在 Controller 之外，走不到 @RestControllerAdvice，
 * 所以单独抽出这段写响应体的逻辑供两处复用。
 */
public final class ProblemJson {

    private ProblemJson() {
    }

    public static void write(HttpServletResponse response, ObjectMapper mapper, ApiError error) throws IOException {
        response.setStatus(error.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), error);
    }
}
