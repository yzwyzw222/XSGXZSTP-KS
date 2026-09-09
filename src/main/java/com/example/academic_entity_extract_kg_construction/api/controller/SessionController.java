package com.example.academic_entity_extract_kg_construction.api.controller;

import com.example.academic_entity_extract_kg_construction.api.dto.request.LoginRequest;
import com.example.academic_entity_extract_kg_construction.api.dto.response.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        if (!adminUsername.equals(request.getUsername())
                || !adminPassword.equals(request.getPassword())) {
            ProblemDetail problem = ProblemDetail.builder()
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .title("Unauthorized")
                    .detail("Invalid username or password")
                    .instance(servletRequest.getRequestURI())
                    .errorCode("AUTH_FAILED")
                    .traceId(UUID.randomUUID().toString().replace("-", ""))
                    .build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.valueOf("application/problem+json"))
                    .body(problem);
        }

        HttpSession oldSession = servletRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute("user", request.getUsername());
        session.setAttribute("role", "ADMIN");
        return ResponseEntity.ok(Map.of("message", "Login successful", "username", request.getUsername()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        }
        String token = csrfToken != null ? csrfToken.getToken() : "";
        return ResponseEntity.ok(Map.of("token", token, "headerName", "X-XSRF-TOKEN"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session, HttpServletRequest request) {
        String user = (String) session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (user == null) {
            ProblemDetail problem = ProblemDetail.builder()
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .title("Unauthorized")
                    .detail("Not authenticated")
                    .instance(request.getRequestURI())
                    .errorCode("NOT_AUTHENTICATED")
                    .traceId(UUID.randomUUID().toString().replace("-", ""))
                    .build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.valueOf("application/problem+json"))
                    .body(problem);
        }
        return ResponseEntity.ok(Map.of("username", user, "role", role));
    }
}
