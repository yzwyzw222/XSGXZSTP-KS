package com.aacv.system.source.infrastructure.crossref;

import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.source.application.port.DataSourceAdapter;
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
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CrossrefDataSourceAdapter implements DataSourceAdapter {

    private static final Pattern DOI_PATTERN = Pattern.compile("^10\\.\\d{4,9}/\\S+$");
    private static final Pattern ORCID_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-[\\dX]{4}$");
    private static final Pattern ROR_PATTERN = Pattern.compile("^(?:https://ror\\.org/)?0[a-hj-km-np-tv-z0-9]{6}[0-9]{2}$");
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 502, 503, 504);
    private static final SourceCapabilities CAPABILITIES = new SourceCapabilities(
            Set.of(
                    SourceFilter.PUBLICATION_DATE,
                    SourceFilter.KEYWORD,
                    SourceFilter.DOI,
                    SourceFilter.ORCID,
                    SourceFilter.ROR,
                    SourceFilter.UPDATED_AT),
            IncrementalMode.CLOSED_INDEX_DATE_WINDOW,
            100,
            true);

    private final CrossrefHttpTransport transport;
    private final CrossrefResponseParser responseParser;
    private final Clock clock;
    private final RetrySleeper retrySleeper;

    @Autowired
    public CrossrefDataSourceAdapter(
            CrossrefHttpTransport transport,
            CrossrefResponseParser responseParser,
            Clock clock) {
        this(transport, responseParser, clock, duration -> Thread.sleep(duration.toMillis()));
    }

    CrossrefDataSourceAdapter(
            CrossrefHttpTransport transport,
            CrossrefResponseParser responseParser,
            Clock clock,
            RetrySleeper retrySleeper) {
        this.transport = transport;
        this.responseParser = responseParser;
        this.clock = clock;
        this.retrySleeper = retrySleeper;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.CROSSREF;
    }

    @Override
    public String parserVersion() {
        return "crossref-v2";
    }

    @Override
    public SourceValidationResult validate(SourceConnectionSettings settings, CrawlScope scope) {
        if (settings == null || scope == null) {
            return SourceValidationResult.invalid(List.of("Crossref连接配置和采集范围不能为空"));
        }
        List<String> errors = new ArrayList<>();
        if (settings.maxConcurrency() != 1) {
            errors.add("Crossref单来源并发数必须为1");
        }
        if ((scope.publicationDateFrom() == null) != (scope.publicationDateTo() == null)) {
            errors.add("Crossref发表日期筛选必须提供闭合起止值");
        }
        validateValues(scope.dois(), DOI_PATTERN, "DOI", errors);
        validateValues(scope.orcids(), ORCID_PATTERN, "ORCID", errors);
        validateValues(scope.rorIds(), ROR_PATTERN, "ROR", errors);
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
        } catch (CrossrefClientException exception) {
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
            throw new IllegalArgumentException("Crossref游标不能为空");
        }
        AttemptResult attemptResult = executeWithRetry(
                settings, () -> transport.fetchWorks(settings, scope, cursor));
        CrossrefResponseParser.ParsedPage parsed = responseParser.parsePage(
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
                CrossrefHttpResponse response = operation.execute();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return new AttemptResult(response, requestCount);
                }
                boolean retryable = RETRYABLE_STATUSES.contains(response.statusCode());
                if (!retryable || requestCount > settings.maxRetries()) {
                    throw new CrossrefClientException(
                            "HTTP_" + response.statusCode(),
                            retryable,
                            response.statusCode(),
                            "Crossref请求返回HTTP " + response.statusCode());
                }
                sleep(response.retryAfter() == null
                        ? exponentialBackoff(requestCount)
                        : response.retryAfter());
            } catch (CrossrefClientException exception) {
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
            throw new CrossrefClientException(
                    "INTERRUPTED", false, null, "Crossref重试等待被中断", exception);
        }
    }

    private void validateValues(
            List<String> values, Pattern pattern, String label, List<String> errors) {
        for (String value : values) {
            if (!pattern.matcher(value.trim()).matches()) {
                errors.add(label + "格式无效");
            }
        }
    }

    private record AttemptResult(CrossrefHttpResponse response, int requestCount) {
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    @FunctionalInterface
    private interface RequestOperation {
        CrossrefHttpResponse execute();
    }
}
