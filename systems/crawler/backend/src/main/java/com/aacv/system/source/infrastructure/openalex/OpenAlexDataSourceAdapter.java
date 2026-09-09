package com.aacv.system.source.infrastructure.openalex;

import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.source.application.port.DataSourceAdapter;
import com.aacv.system.source.application.SourceClientException;
import com.aacv.system.source.application.SourceQuotaExhaustedException;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourceCapabilities;
import com.aacv.system.source.domain.SourceCapabilities.IncrementalMode;
import com.aacv.system.source.domain.SourceCapabilities.SourceFilter;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourcePage;
import com.aacv.system.source.domain.SourceProbeResult;
import com.aacv.system.source.domain.SourceType;
import com.aacv.system.source.domain.SourceValidationResult;
import com.aacv.system.source.domain.SourceWork;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenAlexDataSourceAdapter implements DataSourceAdapter {

    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 502, 503, 504);
    private static final SourceCapabilities CAPABILITIES = new SourceCapabilities(
            Set.of(
                    SourceFilter.PUBLICATION_DATE,
                    SourceFilter.KEYWORD,
                    SourceFilter.AUTHOR_ID,
                    SourceFilter.INSTITUTION_ID),
            IncrementalMode.ROLLING_PUBLICATION_DATE_WINDOW,
            100,
            true);

    private final OpenAlexHttpTransport transport;
    private final OpenAlexResponseParser responseParser;
    private final Clock clock;
    private final RetrySleeper retrySleeper;

    @Autowired
    public OpenAlexDataSourceAdapter(
            OpenAlexHttpTransport transport,
            OpenAlexResponseParser responseParser,
            Clock clock) {
        this(transport, responseParser, clock, duration -> Thread.sleep(duration.toMillis()));
    }

    OpenAlexDataSourceAdapter(
            OpenAlexHttpTransport transport,
            OpenAlexResponseParser responseParser,
            Clock clock,
            RetrySleeper retrySleeper) {
        this.transport = transport;
        this.responseParser = responseParser;
        this.clock = clock;
        this.retrySleeper = retrySleeper;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.OPENALEX;
    }

    @Override
    public String parserVersion() {
        return "openalex-v2";
    }

    @Override
    public SourceValidationResult validate(SourceConnectionSettings settings, CrawlScope scope) {
        if (settings == null || scope == null) {
            return SourceValidationResult.invalid(List.of("OpenAlex连接配置和采集范围不能为空"));
        }
        List<String> errors = new ArrayList<>();
        validateIds(scope.authorIds(), "A", "作者", errors);
        validateIds(scope.institutionIds(), "I", "机构", errors);
        if (!scope.dois().isEmpty() || !scope.orcids().isEmpty() || !scope.rorIds().isEmpty()
                || scope.updatedFrom() != null || scope.updatedUntil() != null) {
            errors.add("OpenAlex参数版本1不接受Crossref专用筛选条件");
        }
        return errors.isEmpty() ? SourceValidationResult.success() : SourceValidationResult.invalid(errors);
    }

    @Override
    public SourceProbeResult probe(SourceConnectionSettings settings) {
        try {
            AttemptResult result = executeWithRetry(settings, () -> transport.probe(settings));
            return new SourceProbeResult(
                    true,
                    result.response().statusCode(),
                    null,
                    result.response().responseMetadata(),
                    clock.instant());
        } catch (SourceClientException exception) {
            return new SourceProbeResult(
                    false,
                    exception.statusCode(),
                    exception.category(),
                    java.util.Map.of(),
                    clock.instant());
        }
    }

    @Override
    public SourcePage fetchPage(
            SourceConnectionSettings settings, CrawlScope scope, OpaqueCursor cursor) {
        SourceValidationResult validation = validate(settings, scope);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("；", validation.errors()));
        }
        if (cursor == null) {
            throw new IllegalArgumentException("OpenAlex游标不能为空");
        }
        AttemptResult attemptResult = executeWithRetry(settings, () -> transport.fetchWorks(settings, scope, cursor));
        OpenAlexResponseParser.ParsedPage parsed = responseParser.parsePage(
                attemptResult.response().body(), attemptResult.response().responseMetadata());
        return new SourcePage(
                parsed.records(), parsed.nextCursor(), attemptResult.requestCount(), parsed.responseMetadata());
    }

    @Override
    public SourceWork parse(RawSourceRecord rawRecord) {
        return responseParser.parseWork(rawRecord);
    }

    @Override
    public SourceCapabilities capabilities() {
        return CAPABILITIES;
    }

    private AttemptResult executeWithRetry(
            SourceConnectionSettings settings, RequestOperation operation) {
        int requestCount = 0;
        while (true) {
            requestCount++;
            try {
                OpenAlexHttpResponse response = operation.execute();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return new AttemptResult(response, requestCount);
                }
                java.time.Instant resetAt = OpenAlexQuota.resetAt(response.responseMetadata(), clock.instant());
                if (response.statusCode() == 429 && resetAt != null) {
                    throw new SourceQuotaExhaustedException(resetAt);
                }
                boolean retryable = RETRYABLE_STATUSES.contains(response.statusCode());
                if (!retryable || requestCount > settings.maxRetries()) {
                    throw new OpenAlexClientException(
                            "HTTP_" + response.statusCode(),
                            retryable,
                            response.statusCode(),
                            "OpenAlex请求返回HTTP " + response.statusCode());
                }
                sleep(response.retryAfter() == null
                        ? exponentialBackoff(requestCount)
                        : response.retryAfter());
            } catch (OpenAlexClientException exception) {
                if (!exception.retryable() || requestCount > settings.maxRetries()) {
                    throw exception;
                }
                sleep(exponentialBackoff(requestCount));
            }
        }
    }

    private Duration exponentialBackoff(int requestCount) {
        long upperBound = Math.min(30_000, 500L << Math.min(requestCount - 1, 6));
        long lowerBound = Math.max(1, upperBound / 2);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(lowerBound, upperBound + 1));
    }

    private void sleep(Duration duration) {
        try {
            retrySleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAlexClientException(
                    "INTERRUPTED", false, null, "OpenAlex重试等待被中断", exception);
        }
    }

    private void validateIds(
            List<String> ids, String prefix, String label, List<String> errors) {
        for (String id : ids) {
            if (!id.matches("(?:https://openalex\\.org/)?" + prefix + "\\d+")) {
                errors.add(label + "OpenAlex ID格式无效");
            }
        }
    }

    private record AttemptResult(OpenAlexHttpResponse response, int requestCount) {
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    @FunctionalInterface
    private interface RequestOperation {
        OpenAlexHttpResponse execute();
    }
}
