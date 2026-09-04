package com.aacv.system.source.infrastructure.crossref;

import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourceConnectionSettings;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
class CrossrefHttpTransport {

    static final String SELECT_FIELDS = String.join(",",
            "DOI", "URL", "title", "type", "published", "issued",
            "container-title", "ISSN", "issn-type", "author", "subject", "reference",
            "abstract", "indexed", "publisher");

    private final CrossrefRestClientFactory clientFactory;
    private final CrossrefRequestGate requestGate;

    CrossrefHttpTransport(CrossrefRestClientFactory clientFactory, CrossrefRequestGate requestGate) {
        this.clientFactory = clientFactory;
        this.requestGate = requestGate;
    }

    CrossrefHttpResponse fetchWorks(
            SourceConnectionSettings settings, CrawlScope scope, OpaqueCursor cursor) {
        return execute(settings, buildWorksUri(scope, cursor));
    }

    CrossrefHttpResponse probe(SourceConnectionSettings settings) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("rows", "0");
        addContactEmail(parameters);
        return execute(settings, buildUri(parameters));
    }

    URI buildWorksUri(CrawlScope scope, OpaqueCursor cursor) {
        int rows = Math.min(100, scope.maxRecords());
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("rows", Integer.toString(rows));
        parameters.put("cursor", cursor.value());
        parameters.put("select", SELECT_FIELDS);
        String filter = buildFilter(scope);
        if (!filter.isBlank()) {
            parameters.put("filter", filter);
        }
        if (scope.keyword() != null) {
            parameters.put("query", scope.keyword());
        }
        addContactEmail(parameters);
        return buildUri(parameters);
    }

    private CrossrefHttpResponse execute(SourceConnectionSettings settings, URI uri) {
        RestClient client = clientFactory.create(settings);
        try (CrossrefRequestGate.Permit ignored = requestGate.acquire(settings)) {
            CrossrefHttpResponse result = client.get().uri(uri).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                Map<String, String> metadata = responseMetadata(response.getHeaders());
                Duration retryAfter = retryAfter(response.getHeaders()).orElse(null);
                if (status < 200 || status >= 300) {
                    return new CrossrefHttpResponse(status, new byte[0], retryAfter, metadata);
                }
                MediaType contentType = response.getHeaders().getContentType();
                if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                    throw new CrossrefClientException(
                            "CONTENT_TYPE", false, status, "Crossref返回了非JSON响应");
                }
                byte[] body = readBounded(response.getBody(), settings.maxResponseBytes(), status);
                return new CrossrefHttpResponse(status, body, retryAfter, metadata);
            });
            requestGate.updatePolicy(result.responseMetadata());
            return result;
        } catch (CrossrefClientException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw classifyNetworkFailure(exception);
        }
    }

    static byte[] readBounded(InputStream input, int maxResponseBytes, int status) throws IOException {
        byte[] body = input.readNBytes(maxResponseBytes + 1);
        if (body.length > maxResponseBytes) {
            throw new CrossrefClientException(
                    "RESPONSE_TOO_LARGE", false, status, "Crossref响应超过配置的解压后大小上限");
        }
        return body;
    }

    private String buildFilter(CrawlScope scope) {
        List<String> filters = new ArrayList<>();
        if (scope.publicationDateFrom() != null) {
            filters.add("from-pub-date:" + scope.publicationDateFrom());
            filters.add("until-pub-date:" + scope.publicationDateTo());
        }
        if (scope.updatedFrom() != null) {
            filters.add("from-index-date:" + DateTimeFormatter.ISO_INSTANT.format(scope.updatedFrom()));
            filters.add("until-index-date:" + DateTimeFormatter.ISO_INSTANT.format(scope.updatedUntil()));
        }
        addFilter(filters, "doi", scope.dois());
        addFilter(filters, "orcid", scope.orcids());
        addFilter(filters, "ror-id", scope.rorIds());
        return String.join(",", filters);
    }

    private void addFilter(List<String> filters, String name, List<String> values) {
        if (!values.isEmpty()) {
            filters.add(name + ":" + String.join("|", values));
        }
    }

    private void addContactEmail(Map<String, String> parameters) {
        if (clientFactory.contactEmail() != null) {
            parameters.put("mailto", clientFactory.contactEmail());
        }
    }

    private URI buildUri(Map<String, String> parameters) {
        String query = parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        return URI.create(DataSourceConfiguration.CROSSREF_BASE_URI + "/works?" + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, String> responseMetadata(HttpHeaders headers) {
        Map<String, String> metadata = new LinkedHashMap<>();
        copyHeader(headers, metadata, "X-API-Pool");
        copyHeader(headers, metadata, "X-Rate-Limit-Limit");
        copyHeader(headers, metadata, "X-Rate-Limit-Interval");
        copyHeader(headers, metadata, "X-Concurrency-Limit");
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
                return Optional.of(duration.isNegative() ? Duration.ZERO
                        : duration.compareTo(Duration.ofMinutes(5)) > 0
                                ? Duration.ofMinutes(5) : duration);
            } catch (DateTimeParseException invalidDate) {
                return Optional.empty();
            }
        }
    }

    private CrossrefClientException classifyNetworkFailure(ResourceAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return new CrossrefClientException(
                        "TIMEOUT", true, null, "Crossref请求超时", exception);
            }
            if (cause instanceof UnknownHostException) {
                return new CrossrefClientException(
                        "DNS_TEMPORARY", true, null, "Crossref域名解析暂时失败", exception);
            }
            if (cause instanceof ConnectException) {
                return new CrossrefClientException(
                        "CONNECTION", false, null, "无法连接Crossref服务", exception);
            }
            cause = cause.getCause();
        }
        return new CrossrefClientException(
                "NETWORK", false, null, "Crossref网络请求失败", exception);
    }
}
