package com.xsyu.academicgraph.infrastructure.openalex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAlex HTTP 传输层（从 feature/Luo 移植的简化版）。
 * 原版支持游标翻页 + 请求门控 + 多维度过滤；本平台只做"关键词搜索单页"，
 * 所以砍掉 cursor/filter/requestGate，只保留：
 *  - 固定 select 字段（只取导入需要的 12 个字段，响应更小更快）
 *  - 超时控制（JDK HttpClient 的连接/读取超时）
 *  - X-RateLimit 头解析（留给日志观察配额消耗）
 *  - Retry-After 解析（交给 CrawlService 的重试循环决定等多久）
 *  - 网络异常分类（超时/DNS/连接拒绝分别归类，决定是否重试）
 */
@Component
public class OpenAlexHttpTransport {

    private static final Logger log = LoggerFactory.getLogger(OpenAlexHttpTransport.class);

    /** 单页响应体积上限（25 条论文的 JSON 通常只有几百 KB，5MB 已经非常宽裕） */
    private static final int MAX_RESPONSE_BYTES = 5_000_000;

    /** 只请求导入链路用得到的字段，省带宽也加快解析 */
    static final String SELECT_FIELDS = String.join(",",
            "id", "doi", "title", "type", "language", "publication_date", "primary_location",
            "authorships", "topics", "referenced_works", "abstract_inverted_index",
            "cited_by_count");

    private final OpenAlexProperties properties;
    private final RestClient client;

    public OpenAlexHttpTransport(OpenAlexProperties properties) {
        this.properties = properties;
        // RestClient 线程安全，构造时建一次全程复用；
        // JDK HttpClient 固定 followRedirects(NEVER)：OpenAlex 不会重定向，出现重定向说明配置错了
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getResponseTimeout());
        this.client = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "AcademicGraph/1.0 (course project)")
                .build();
    }

    /**
     * 按关键词搜索一页论文。
     * 返回原始响应（状态码 + 字节体 + Retry-After + 配额头），状态判定交给调用方：
     * 这样 CrawlService 能看到 429/5xx 并决定重试策略。
     */
    public OpenAlexHttpResponse fetchWorks(String keyword, int perPage) {
        try {
            return client.get().uri(uriBuilder -> buildWorksUri(uriBuilder, keyword, perPage))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        Map<String, String> metadata = responseMetadata(response.getHeaders());
                        Duration retryAfter = retryAfter(response.getHeaders()).orElse(null);
                        if (status < 200 || status >= 300) {
                            // 非 2xx 不读响应体（可能是 HTML 错误页），只带状态码和头信息回去
                            return new OpenAlexHttpResponse(status, new byte[0], retryAfter, metadata);
                        }
                        MediaType contentType = response.getHeaders().getContentType();
                        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                            throw new OpenAlexClientException(
                                    "CONTENT_TYPE", false, status, "OpenAlex 返回了非 JSON 响应");
                        }
                        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (body.length > MAX_RESPONSE_BYTES) {
                            throw new OpenAlexClientException(
                                    "RESPONSE_TOO_LARGE", false, status, "OpenAlex 响应超过体积上限");
                        }
                        return new OpenAlexHttpResponse(status, body, retryAfter, metadata);
                    });
        } catch (OpenAlexClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            // RestClient 把所有 IO 异常包成 ResourceAccessException，这里拆开看根因归类
            throw classifyNetworkFailure(exception);
        }
    }

    /** 组装 /works 查询参数：per_page + select 固定字段 + search 关键词 + 可选 mailto */
    private java.net.URI buildWorksUri(UriBuilder uriBuilder, String keyword, int perPage) {
        UriBuilder builder = uriBuilder
                .path("/works")
                .queryParam("per_page", perPage)
                .queryParam("search", keyword)
                .queryParam("select", SELECT_FIELDS);
        if (properties.getMailto() != null && !properties.getMailto().isBlank()) {
            builder.queryParam("mailto", properties.getMailto());
        }
        return builder.build();
    }

    /** 提取 X-RateLimit-* 配额头：记进响应元数据，日志里能看到当天还剩多少配额 */
    private Map<String, String> responseMetadata(HttpHeaders headers) {
        Map<String, String> metadata = new LinkedHashMap<>();
        copyHeader(headers, metadata, "X-RateLimit-Limit");
        copyHeader(headers, metadata, "X-RateLimit-Remaining");
        copyHeader(headers, metadata, "X-RateLimit-Reset");
        return metadata;
    }

    private void copyHeader(HttpHeaders headers, Map<String, String> target, String name) {
        String value = headers.getFirst(name);
        if (value != null && value.length() <= 128) {
            target.put(name, value);
        }
    }

    /**
     * 解析 Retry-After 头：支持两种格式——秒数（"5"）与 HTTP 日期（"Wed, 21 Oct 2015 07:28:00 GMT"）。
     * 统一换算成 Duration 并封顶 5 分钟，防止上游给出离谱的等待时间把线程挂死。
     */
    private Optional<Duration> retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return Optional.of(Duration.ofSeconds(Math.max(0, Math.min(seconds, 300))));
        } catch (NumberFormatException ignored) {
            try {
                Duration duration = Duration.between(
                        Instant.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return Optional.of(duration.isNegative() ? Duration.ZERO
                        : duration.compareTo(Duration.ofMinutes(5)) > 0 ? Duration.ofMinutes(5) : duration);
            } catch (DateTimeParseException invalidDate) {
                log.debug("无法解析 Retry-After 头: {}", value);
                return Optional.empty();
            }
        }
    }

    /** 网络异常分类：超时/DNS 失败视为临时性错误（可重试），连接拒绝等视为环境问题（不重试） */
    private OpenAlexClientException classifyNetworkFailure(ResourceAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return new OpenAlexClientException(
                        "TIMEOUT", true, null, "OpenAlex 请求超时，请稍后重试", exception);
            }
            if (cause instanceof UnknownHostException) {
                return new OpenAlexClientException(
                        "DNS_TEMPORARY", true, null, "OpenAlex 域名解析暂时失败，请检查网络", exception);
            }
            if (cause instanceof ConnectException) {
                return new OpenAlexClientException(
                        "CONNECTION", false, null, "无法连接 OpenAlex 服务，请检查网络", exception);
            }
            cause = cause.getCause();
        }
        return new OpenAlexClientException(
                "NETWORK", false, null, "OpenAlex 网络请求失败", exception);
    }

    /** 一次 HTTP 往返的原始结果：字节体 + 状态码 + 重试等待时长 + 配额头 */
    public record OpenAlexHttpResponse(
            int statusCode,
            byte[] body,
            Duration retryAfter,
            Map<String, String> metadata) {
    }
}
