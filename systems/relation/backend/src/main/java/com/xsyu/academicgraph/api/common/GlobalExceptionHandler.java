package com.xsyu.academicgraph.api.common;

import com.xsyu.academicgraph.infrastructure.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局异常翻译器：把所有异常统一翻译成工程规范的 problem+json 结构。
 * 每个分支对应一类业务/技术错误，前端只需看 errorCode 就能做分支处理：
 *   VALIDATION_FAILED(400) 参数校验失败     AUTH_FAILED(401) 账号密码错误/被禁用
 *   NOT_FOUND(404) 资源不存在              CONFLICT(409) 乐观锁冲突/唯一键冲突/外键占用
 *   FORBIDDEN(403) 无权限                   INTERNAL_ERROR(500) 兜底未知错误
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 日志器：把面向用户屏蔽掉的原始报错留在这里，排查问题时按 traceId 找 */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** @Valid 校验 DTO 失败：逐字段返回中文提示 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "请求参数校验失败", e.getMessage(),
                "VALIDATION_FAILED", request, fieldErrors);
    }

    /** @RequestParam/@PathVariable 上的约束校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = e.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "请求参数校验失败", e.getMessage(),
                "VALIDATION_FAILED", request, fieldErrors);
    }

    /** 业务规则校验失败：重复用户名、DOI 已被占用、引用条目缺字段等（服务层直接抛 IllegalArgumentException） */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "请求参数不合法", e.getMessage(),
                "VALIDATION_FAILED", request, null);
    }

    /** 路径参数类型不匹配（如 /papers/abc） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "请求参数类型错误",
                "参数 " + e.getName() + " 的值无法解析为 " + (e.getRequiredType() == null ? "期望类型" : e.getRequiredType().getSimpleName()),
                "VALIDATION_FAILED", request, null);
    }

    /** 上传文件超过 multipart 大小上限（application.yaml 里配置的 5MB） */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "上传文件过大",
                "导入文件超过大小上限（单文件最大 5MB），请拆分后再上传",
                "VALIDATION_FAILED", request, null);
    }

    /** OpenAlex 上游访问失败（断网/超时/限流/解析失败）：统一 502，重试细节只进日志 */
    @ExceptionHandler(com.xsyu.academicgraph.infrastructure.openalex.OpenAlexClientException.class)
    public ResponseEntity<ApiError> handleOpenAlex(
            com.xsyu.academicgraph.infrastructure.openalex.OpenAlexClientException e, HttpServletRequest request) {
        log.warn("OpenAlex 访问失败 uri={} category={} status={}",
                request.getRequestURI(), e.getCategory(), e.getStatusCode());
        return build(HttpStatus.BAD_GATEWAY, "OpenAlex 数据源访问失败",
                e.getSafeMessage() == null ? "OpenAlex 数据源暂时不可用，请稍后重试" : e.getSafeMessage(),
                "UPSTREAM_ERROR", request, null);
    }

    /** 登录密码错误：统一 401，不区分"用户不存在"与"密码错"（防用户名枚举） */
    @ExceptionHandler({BadCredentialsException.class, org.springframework.security.authentication.InternalAuthenticationServiceException.class})
    public ResponseEntity<ApiError> handleBadCredentials(Exception e, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "登录失败", "用户名或密码错误", "AUTH_FAILED", request, null);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException e, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "登录失败", "账号已被禁用，请联系管理员", "AUTH_FAILED", request, null);
    }

    /** 乐观锁冲突：两个请求同时改了同一份数据，后提交的一方收到 409 */
    @ExceptionHandler({OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleOptimisticLock(Exception e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "数据已被他人修改",
                "该数据在你打开后被其他用户更新，请刷新后重试（version 乐观锁冲突）",
                "CONFLICT", request, null);
    }

    /** 数据库完整性冲突：唯一键重复（如重名关键词/重复 DOI）、外键被占用（删除被引用的作者） */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrityViolation(DataIntegrityViolationException e, HttpServletRequest request) {
        String raw = e.getMostSpecificCause() == null ? e.getMessage() : e.getMostSpecificCause().getMessage();
        // 原始 JDBC 报错（含表名、约束名）只进日志便于排查；返回前端的 detail 必须是用户看得懂的中文
        log.warn("数据完整性冲突 uri={} cause={}", request.getRequestURI(), raw);
        return build(HttpStatus.CONFLICT, "数据冲突", translateIntegrityViolation(raw),
                "CONFLICT", request, null);
    }

    /**
     * 把 MySQL 原始约束报错翻译成中文提示。
     * 两类高频场景：
     *   1. 外键占用（删除仍被引用的记录）：Cannot delete or update a parent row: a foreign key constraint fails (`db`.`子表`, CONSTRAINT ...)
     *   2. 唯一键重复：Duplicate entry '值' for key '表.唯一键名'
     * 命中不了的一律回退到通用文案，绝不把 SQL 细节抛给用户。
     */
    static String translateIntegrityViolation(String raw) {
        String fallback = "操作违反了数据完整性约束（记录重复或仍被其他数据引用）";
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        if (raw.contains("foreign key constraint fails")) {
            Matcher m = FK_CHILD_TABLE.matcher(raw);
            if (m.find()) {
                String child = m.group(1);
                return "该记录仍被「" + FK_TABLE_LABELS.getOrDefault(child, child) + "」引用，请先解除关联后再删除";
            }
            return "该记录仍被其他数据引用，请先解除关联后再删除";
        }
        if (raw.contains("Duplicate entry")) {
            Matcher m = DUPLICATE_KEY.matcher(raw);
            if (m.find()) {
                String value = m.group(1);
                String key = m.group(2);
                return "「" + value + "」已存在，" + UNIQUE_KEY_LABELS.getOrDefault(key, "该字段") + "不允许重复";
            }
            return "记录已存在，唯一键冲突";
        }
        if (raw.contains("cannot be null")) {
            return "必填字段不能为空";
        }
        return fallback;
    }

    /** 从 "CONSTRAINT `fk_xxx` FOREIGN KEY" 前面的 "`db`.`子表`" 中取出子表名（即持有外键、引用别人的那张表） */
    private static final Pattern FK_CHILD_TABLE = Pattern.compile("`[^`]+`\\.`([^`]+)`,\\s*CONSTRAINT");

    /** 从 "Duplicate entry 'xxx' for key 'paper.uk_paper_doi'" 中取出重复值与唯一键名 */
    private static final Pattern DUPLICATE_KEY = Pattern.compile("Duplicate entry '([^']*)' for key '(?:[^']*\\.)?([^']*)'");

    /** 子表名 → 中文说明：告诉用户"是谁还在引用它" */
    private static final Map<String, String> FK_TABLE_LABELS = Map.of(
            "paper_author", "论文署名",
            "paper_keyword", "论文关键词关联",
            "paper_reference", "论文引用关系",
            "paper", "论文（发表渠道或创建者）",
            "sys_user_role", "用户角色授权"
    );

    /** 唯一键名 → 中文说明：告诉用户"哪个字段重复了" */
    private static final Map<String, String> UNIQUE_KEY_LABELS = Map.of(
            "uk_sys_user_username", "用户名",
            "uk_paper_doi", "DOI",
            "uk_keyword_name", "关键词名称",
            "uk_paper_author_position", "同一论文的作者位次",
            "uk_paper_keyword_position", "同一论文的关键词位次",
            "uk_paper_reference", "重复的引用关系",
            "uk_sys_role_code", "角色编码"
    );

    /** 业务层主动抛出的"资源不存在" */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "资源不存在", e.getMessage(), "NOT_FOUND", request, null);
    }

    /** 静态资源 404 交给默认处理，避免吞掉前端路由的 404 语义 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "资源不存在", e.getMessage(), "NOT_FOUND", request, null);
    }

    /** 兜底：任何没预料到的异常都返回 500 + traceId，日志里能按 traceId 定位 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception e, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误",
                e.getClass().getSimpleName() + ": " + e.getMessage(),
                "INTERNAL_ERROR", request, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String title, String detail,
                                           String code, HttpServletRequest request,
                                           List<ApiError.FieldError> fieldErrors) {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        ApiError body = fieldErrors == null
                ? ApiError.of(status.value(), title, detail, request.getRequestURI(), code,
                traceId == null ? "unknown" : traceId)
                : ApiError.of(status.value(), title, detail, request.getRequestURI(), code,
                traceId == null ? "unknown" : traceId, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    /** 业务异常：服务层找不到数据时直接 throw new EntityNotFoundException("...") */
    public static class EntityNotFoundException extends RuntimeException {
        public EntityNotFoundException(String message) {
            super(message);
        }
    }
}
