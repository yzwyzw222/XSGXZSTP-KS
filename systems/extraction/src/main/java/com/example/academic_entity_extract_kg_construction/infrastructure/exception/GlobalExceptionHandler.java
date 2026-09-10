package com.example.academic_entity_extract_kg_construction.infrastructure.exception;

import com.example.academic_entity_extract_kg_construction.api.dto.response.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GraphUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleGraphUnavailable(GraphUnavailableException ex, HttpServletRequest request) {
        log.warn("图谱请求暂不可用：{}", ex.getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Graph Unavailable", ex.getMessage(),
                request.getRequestURI(), "GRAPH_UNAVAILABLE");
    }

    @ExceptionHandler(PaperNotFoundException.class)
    public ResponseEntity<ProblemDetail> handlePaperNotFound(PaperNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Paper Not Found", ex.getMessage(),
                request.getRequestURI(), "PAPER_NOT_FOUND");
    }

    @ExceptionHandler(AuthorNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleAuthorNotFound(AuthorNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Author Not Found", ex.getMessage(),
                request.getRequestURI(), "AUTHOR_NOT_FOUND");
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ProblemDetail> handleExternalApi(ExternalApiException ex, HttpServletRequest request) {
        log.error("External API error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.BAD_GATEWAY, "External Service Error",
                "The external service is temporarily unavailable",
                request.getRequestURI(), "EXTERNAL_API_ERROR");
    }

    @ExceptionHandler(ExtractionException.class)
    public ResponseEntity<ProblemDetail> handleExtraction(ExtractionException ex, HttpServletRequest request) {
        log.error("Extraction error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Extraction Failed",
                "Entity extraction failed, please try again later",
                request.getRequestURI(), "EXTRACTION_FAILED");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ProblemDetail.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ProblemDetail.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        ProblemDetail problem = ProblemDetail.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .title("Bad Request")
                .detail("Request validation failed")
                .instance(request.getRequestURI())
                .errorCode("VALIDATION_FAILED")
                .traceId(getTraceId())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest()
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(problem);
    }

    /** 保留路由与参数错误的 HTTP 语义，避免统一入口把客户端错误误报为后端故障。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleMissingResource(NoResourceFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", "请求的接口不存在",
                request.getRequestURI(), "NOT_FOUND");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex,
                                                               HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", "缺少必要请求参数：" + ex.getParameterName(),
                request.getRequestURI(), "MISSING_PARAMETER");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred",
                request.getRequestURI(), "INTERNAL_ERROR");
    }

    private ResponseEntity<ProblemDetail> buildResponse(HttpStatus status, String title, String detail,
                                                         String instance, String errorCode) {
        ProblemDetail problem = ProblemDetail.builder()
                .status(status.value())
                .title(title)
                .detail(detail)
                .instance(instance)
                .errorCode(errorCode)
                .traceId(getTraceId())
                .build();

        return ResponseEntity.status(status)
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(problem);
    }

    private String getTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            MDC.put("traceId", traceId);
        }
        return traceId;
    }
}
