package com.aacv.system.shared.infrastructure.web;

import com.aacv.system.identity.application.InvalidCredentialsException;
import com.aacv.system.identity.application.UserNotFoundException;
import com.aacv.system.identity.application.UsernameConflictException;
import com.aacv.system.identity.application.VersionConflictException;
import com.aacv.system.graph.application.GraphRebuildInProgressException;
import com.aacv.system.graph.application.GraphQueryTimeoutException;
import com.aacv.system.graph.application.GraphUnavailableException;
import com.aacv.system.export.application.ExportConcurrencyLimitException;
import com.aacv.system.export.application.ExportExpiredException;
import com.aacv.system.export.application.ExportLimitExceededException;
import com.aacv.system.shared.domain.ErrorCode;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBodyValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                request, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "请求字段校验失败");
        List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "字段值无效" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler({
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        ConstraintViolationException.class,
        HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class,
        HttpMediaTypeNotSupportedException.class
    })
    ProblemDetail handleRequestValidation(Exception exception, HttpServletRequest request) {
        return problem(request, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "请求参数或正文格式无效");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleDomainValidation(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "当前账号无权执行该操作");
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleNotFound(UserNotFoundException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ResourceConflictException.class)
    ProblemDetail handleResourceConflict(ResourceConflictException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(GraphUnavailableException.class)
    ProblemDetail handleGraphUnavailable(GraphUnavailableException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.GRAPH_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(GraphQueryTimeoutException.class)
    ProblemDetail handleGraphQueryTimeout(GraphQueryTimeoutException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.GATEWAY_TIMEOUT, ErrorCode.GRAPH_QUERY_TIMEOUT, exception.getMessage());
    }

    @ExceptionHandler(GraphRebuildInProgressException.class)
    ProblemDetail handleGraphRebuildInProgress(
            GraphRebuildInProgressException exception, HttpServletRequest request) {
        return problem(
                request, HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.GRAPH_REBUILD_IN_PROGRESS, exception.getMessage());
    }

    @ExceptionHandler(ExportLimitExceededException.class)
    ProblemDetail handleExportLimitExceeded(
            ExportLimitExceededException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.BAD_REQUEST, ErrorCode.EXPORT_LIMIT_EXCEEDED, exception.getMessage());
    }

    @ExceptionHandler(ExportConcurrencyLimitException.class)
    ProblemDetail handleExportConcurrencyLimit(
            ExportConcurrencyLimitException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, ErrorCode.EXPORT_CONCURRENCY_LIMIT, exception.getMessage());
    }

    @ExceptionHandler(ExportExpiredException.class)
    ProblemDetail handleExportExpired(ExportExpiredException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.GONE, ErrorCode.EXPORT_EXPIRED, exception.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "资源不存在");
    }

    @ExceptionHandler(UsernameConflictException.class)
    ProblemDetail handleUsernameConflict(UsernameConflictException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, ErrorCode.USERNAME_CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(VersionConflictException.class)
    ProblemDetail handleVersionConflict(VersionConflictException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT, "用户数据已更新，请刷新后重试");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        LOGGER.error("未处理异常，traceId={}", traceId, exception);
        return problem(request, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "服务器内部错误");
    }

    private ProblemDetail problem(
            HttpServletRequest request, HttpStatus status, ErrorCode errorCode, String detail) {
        request.setAttribute(com.aacv.system.operations.infrastructure.web.FailedOperationAuditFilter.ERROR_CODE, errorCode.name());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode.name());
        problem.setProperty("traceId", traceId(request));
        return problem;
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceContext.ATTRIBUTE_NAME);
        return traceId instanceof String value ? value : TraceContext.current();
    }
}
