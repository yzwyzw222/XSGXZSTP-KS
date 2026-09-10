package com.xsyu.academicgraph.infrastructure.openalex;

/**
 * OpenAlex 客户端异常（从 feature/Luo 移植，去掉了父类层次直接继承 RuntimeException）。
 * 携带错误分类与"是否值得重试"标志：信息采集服务据此决定指数退避重试还是立刻放弃，
 * 全局异常处理器则统一翻译成 502 + 中文 problem+json（safeMessage 不含技术细节）。
 */
public class OpenAlexClientException extends RuntimeException {

    /** 错误分类：HTTP_429 / TIMEOUT / PARSE / NETWORK / INTERRUPTED 等，便于日志统计 */
    private final String category;

    /** 是否可重试：429/502/503/504 与网络抖动可以重试，解析失败、4xx 业务错误不行 */
    private final boolean retryable;

    /** 上游返回的 HTTP 状态码；网络层失败（超时/DNS）时为 null */
    private final Integer statusCode;

    /** 可以安全展示给用户的中文提示（不暴露原始响应体等内部细节） */
    private final String safeMessage;

    public OpenAlexClientException(String category, boolean retryable, Integer statusCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.category = category;
        this.retryable = retryable;
        this.statusCode = statusCode;
        this.safeMessage = safeMessage;
    }

    public OpenAlexClientException(String category, boolean retryable, Integer statusCode, String safeMessage) {
        this(category, retryable, statusCode, safeMessage, null);
    }

    public String getCategory() {
        return category;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getSafeMessage() {
        return safeMessage;
    }
}
