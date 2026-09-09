package com.aacv.system.identity.infrastructure.security;

import com.aacv.system.shared.domain.ErrorCode;
import com.aacv.system.shared.infrastructure.web.ProblemResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter problemResponseWriter;

    public ApiAuthenticationEntryPoint(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        problemResponseWriter.write(
                request,
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.AUTHENTICATION_REQUIRED,
                "请先登录或重新登录");
    }
}
