package com.aacv.system.shared.infrastructure.web;

import com.aacv.system.shared.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            ErrorCode errorCode,
            String detail)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setTitle(titleFor(status));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode.name());
        problem.setProperty("traceId", traceId(request));
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceContext.ATTRIBUTE_NAME);
        return traceId instanceof String value ? value : TraceContext.current();
    }

    private String titleFor(int status) {
        return switch (status) {
            case 400 -> "请求无效";
            case 401 -> "需要认证";
            case 403 -> "禁止访问";
            case 404 -> "资源不存在";
            case 409 -> "请求冲突";
            default -> "服务器内部错误";
        };
    }
}
