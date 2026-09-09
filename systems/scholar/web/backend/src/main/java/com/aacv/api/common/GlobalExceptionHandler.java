package com.aacv.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ProblemResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(e -> new ProblemResponse.FieldError(e.getField(), e.getDefaultMessage()))
                .collect(Collectors.toList());

        ProblemResponse problem = new ProblemResponse(
                400,
                "Bad Request",
                "请求参数校验失败",
                request.getRequestURI(),
                "VALIDATION_FAILED"
        );
        problem.setFieldErrors(fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        ProblemResponse problem = new ProblemResponse(
                401,
                "Unauthorized",
                "未登录或会话已过期",
                request.getRequestURI(),
                "UNAUTHORIZED"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        ProblemResponse problem = new ProblemResponse(
                403,
                "Forbidden",
                "没有权限执行此操作",
                request.getRequestURI(),
                "ACCESS_DENIED"
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        ProblemResponse problem = new ProblemResponse(
                400,
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI(),
                "BAD_REQUEST"
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemResponse> handleNotFound(
            Exception ex, HttpServletRequest request) {
        ProblemResponse problem = new ProblemResponse(
                404,
                "Not Found",
                ex instanceof NoResourceFoundException
                        ? "请求的资源不存在"
                        : ex.getMessage(),
                request.getRequestURI(),
                "RESOURCE_NOT_FOUND"
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemResponse> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        ProblemResponse problem = new ProblemResponse(
                409,
                "Conflict",
                ex.getMessage(),
                request.getRequestURI(),
                "CONFLICT"
        );

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ProblemResponse problem = new ProblemResponse(
                500,
                "Internal Server Error",
                "服务器内部错误，请稍后重试",
                request.getRequestURI(),
                "INTERNAL_ERROR"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}