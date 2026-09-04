package com.aacv.system.source.infrastructure.openalex;

import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourceConnectionSettings;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
class OpenAlexHttpTransport {

    static final String SELECT_FIELDS = String.join(",",
            "id", "doi", "title", "type", "language", "publication_date", "primary_location",
            "authorships", "topics", "referenced_works", "abstract_inverted_index");

    private final OpenAlexRestClientFactory clientFactory;
    private final OpenAlexRequestGate requestGate;

    OpenAlexHttpTransport(OpenAlexRestClientFactory clientFactory, OpenAlexRequestGate requestGate) {
        this.clientFactory = clientFactory;
        this.requestGate = requestGate;
    }

    OpenAlexHttpResponse fetchWorks(
            SourceConnectionSettings settings, CrawlScope scope, OpaqueCursor cursor) {
        int perPage = Math.min(100, scope.maxRecords());
        return execute(settings, uriBuilder -> {
            UriBuilder builder = uriBuilder
                    .path("/works")
                    .queryParam("per_page", perPage)
                    .queryParam("cursor", cursor.value())
                    .queryParam("select", SELECT_FIELDS);
            String filter = buildFilter(scope);
            if (!filter.isBlank()) {
                builder.queryParam("filter", filter);
            }
            if (scope.keyword() != null) {
                builder.queryParam("search", scope.keyword());
            }
            return builder.build();
        });
    }

    OpenAlexHttpResponse probe(SourceConnectionSettings settings) {
        return execute(settings, uriBuilder -> uriBuilder
                .path("/works")
                .queryParam("per_page", 1)
                .queryParam("select", "id")
                .build());
    }

    private OpenAlexHttpResponse execute(
            SourceConnectionSettings settings,
            java.util.function.Function<UriBuilder, java.net.URI> uriFunction) {
        RestClient client = clientFactory.create(settings);
        try (OpenAlexRequestGate.Permit ignored = requestGate.acquire(settings)) {
            return client.get().uri(uriFunction).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                Map<String, String> metadata = responseMetadata(response.getHeaders());
                Duration retryAfter = retryAfter(response.getHeaders()).orElse(null);
                if (status < 200 || status >= 300) {
                    return new OpenAlexHttpResponse(status, new byte[0], retryAfter, metadata);
                }
                MediaType contentType = response.getHeaders().getContentType();
                if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                    throw new OpenAlexClientException(
                            "CONTENT_TYPE", false, status, "OpenAlex返回了非JSON响应");
                }
                byte[] body = response.getBody().readNBytes(settings.maxResponseBytes() + 1);
                if (body.length > settings.maxResponseBytes()) {
                    throw new OpenAlexClientException(
                            "RESPONSE_TOO_LARGE", false, status, "OpenAlex响应超过配置的解压后大小上限");
                }
                return new OpenAlexHttpResponse(status, body, retryAfter, metadata);
            });
        } catch (OpenAlexClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw classifyNetworkFailure(exception);
        }
    }

    private String buildFilter(CrawlScope scope) {
        java.util.List<String> filters = new java.util.ArrayList<>();
        if (scope.publicationDateFrom() != null) {
            filters.add("from_publication_date:" + scope.publicationDateFrom());
        }
        if (scope.publicationDateTo() != null) {
            filters.add("to_publication_date:" + scope.publicationDateTo());
        }
        if (!scope.authorIds().isEmpty()) {
            filters.add("author.id:" + joinIds(scope.authorIds(), 'A'));
        }
        if (!scope.institutionIds().isEmpty()) {
            filters.add("institutions.id:" + joinIds(scope.institutionIds(), 'I'));
        }
        return String.join(",", filters);
    }

    private String joinIds(java.util.List<String> ids, char prefix) {
        return ids.stream().map(id -> {
            String trimmed = id.trim();
            int separator = trimmed.lastIndexOf('/');
            String normalized = separator >= 0 ? trimmed.substring(separator + 1) : trimmed;
            if (!normalized.matches(prefix + "\\d+")) {
                throw new IllegalArgumentException("OpenAlex过滤ID格式无效");
            }
            return normalized;
        }).collect(java.util.stream.Collectors.joining("|"));
    }

    private Map<String, String> responseMetadata(HttpHeaders headers) {
        Map<String, String> metadata = new LinkedHashMap<>();
        copyHeader(headers, metadata, "X-RateLimit-Limit");
        copyHeader(headers, metadata, "X-RateLimit-Remaining");
        copyHeader(headers, metadata, "X-RateLimit-Credits-Used");
        copyHeader(headers, metadata, "X-RateLimit-Reset");
        return metadata;
    }

    private void copyHeader(HttpHeaders headers, Map<String, String> target, String name) {
        String value = headers.getFirst(name);
        if (value != null && value.length() <= 128) {
            target.put(name, value);
        }
    }

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
                        java.time.Instant.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return Optional.of(duration.isNegative() ? Duration.ZERO : duration.compareTo(Duration.ofMinutes(5)) > 0
                        ? Duration.ofMinutes(5) : duration);
            } catch (DateTimeParseException invalidDate) {
                return Optional.empty();
            }
        }
    }

    private OpenAlexClientException classifyNetworkFailure(ResourceAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return new OpenAlexClientException(
                        "TIMEOUT", true, null, "OpenAlex请求超时", exception);
            }
            if (cause instanceof UnknownHostException) {
                return new OpenAlexClientException(
                        "DNS_TEMPORARY", true, null, "OpenAlex域名解析暂时失败", exception);
            }
            if (cause instanceof ConnectException) {
                return new OpenAlexClientException(
                        "CONNECTION", false, null, "无法连接OpenAlex服务", exception);
            }
            cause = cause.getCause();
        }
        return new OpenAlexClientException(
                "NETWORK", false, null, "OpenAlex网络请求失败", exception);
    }
}
