package com.aacv.system.identity.infrastructure.security;

import com.aacv.system.shared.domain.ErrorCode;
import com.aacv.system.shared.infrastructure.web.ProblemResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter problemResponseWriter;

    public ApiAccessDeniedHandler(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        boolean csrfFailure = accessDeniedException instanceof CsrfException;
        problemResponseWriter.write(
                request,
                response,
                HttpServletResponse.SC_FORBIDDEN,
                csrfFailure ? ErrorCode.CSRF_INVALID : ErrorCode.ACCESS_DENIED,
                csrfFailure ? "CSRF令牌缺失或无效" : "当前账号无权执行该操作");
    }
}
