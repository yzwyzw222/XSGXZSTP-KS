package com.aacv.system.source.infrastructure.openalex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourcePage;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class OpenAlexDataSourceAdapterTests {

    private static final Instant NOW = Instant.parse("2026-09-02T01:00:00Z");

    private OpenAlexHttpTransport transport;
    private AtomicReference<Duration> slept;
    private OpenAlexDataSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        transport = mock(OpenAlexHttpTransport.class);
        slept = new AtomicReference<>();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        adapter = new OpenAlexDataSourceAdapter(
                transport,
                new OpenAlexResponseParser(new ObjectMapper(), clock),
                clock,
                slept::set);
    }

    @Test
    void retryableStatusHonorsRetryAfterAndCountsRequests() throws IOException {
        OpenAlexHttpResponse rateLimited = new OpenAlexHttpResponse(
                429, new byte[0], Duration.ofSeconds(2), Map.of("X-RateLimit-Remaining", "0"));
        OpenAlexHttpResponse success = new OpenAlexHttpResponse(
                200, fixture(), null, Map.of("X-RateLimit-Remaining", "997"));
        when(transport.fetchWorks(any(), any(), any())).thenReturn(rateLimited, success);

        SourcePage page = adapter.fetchPage(settings(2), scope(), OpaqueCursor.first());

        assertEquals(2, page.requestCount());
        assertEquals(Duration.ofSeconds(2), slept.get());
        assertEquals(1, page.records().size());
        verify(transport, times(2)).fetchWorks(any(), any(), any());
    }

    @Test
    void clientErrorIsNeverRetried() {
        when(transport.fetchWorks(any(), any(), any())).thenReturn(
                new OpenAlexHttpResponse(400, new byte[0], null, Map.of()));

        OpenAlexClientException exception = assertThrows(
                OpenAlexClientException.class,
                () -> adapter.fetchPage(settings(3), scope(), OpaqueCursor.first()));

        assertEquals("HTTP_400", exception.category());
        assertFalse(exception.retryable());
        verify(transport).fetchWorks(any(), any(), any());
    }

    @Test
    void unsupportedOpenAlexIdIsRejectedBeforeNetworkCall() {
        CrawlScope invalidScope = new CrawlScope(
                null, null, null, List.of("not-an-author"), List.of(), 1, 10);

        assertFalse(adapter.validate(settings(0), invalidScope).valid());
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.fetchPage(settings(0), invalidScope, OpaqueCursor.first()));
        verify(transport, times(0)).fetchWorks(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void authorizationFailuresAreNeverRetried(int status) {
        when(transport.fetchWorks(any(), any(), any())).thenReturn(
                new OpenAlexHttpResponse(status, new byte[0], null, Map.of()));

        OpenAlexClientException exception = assertThrows(
                OpenAlexClientException.class,
                () -> adapter.fetchPage(settings(3), scope(), OpaqueCursor.first()));

        assertEquals("HTTP_" + status, exception.category());
        assertFalse(exception.retryable());
        verify(transport).fetchWorks(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504})
    void temporaryServerFailuresUseFiniteRetry(int status) throws IOException {
        when(transport.fetchWorks(any(), any(), any())).thenReturn(
                new OpenAlexHttpResponse(status, new byte[0], null, Map.of()),
                new OpenAlexHttpResponse(200, fixture(), null, Map.of()));

        SourcePage page = adapter.fetchPage(settings(1), scope(), OpaqueCursor.first());

        assertEquals(2, page.requestCount());
        verify(transport, times(2)).fetchWorks(any(), any(), any());
    }

    @Test
    void invalidJsonAndOversizedResponseFailureAreNotRetried() {
        when(transport.fetchWorks(any(), any(), any())).thenReturn(
                new OpenAlexHttpResponse(200, "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8), null, Map.of()));
        OpenAlexClientException invalidJson = assertThrows(
                OpenAlexClientException.class,
                () -> adapter.fetchPage(settings(3), scope(), OpaqueCursor.first()));
        assertFalse(invalidJson.retryable());

        org.mockito.Mockito.reset(transport);
        when(transport.fetchWorks(any(), any(), any())).thenThrow(
                new OpenAlexClientException("RESPONSE_TOO_LARGE", false, 200, "OpenAlex响应体超过配置上限"));
        OpenAlexClientException oversized = assertThrows(
                OpenAlexClientException.class,
                () -> adapter.fetchPage(settings(3), scope(), OpaqueCursor.first()));
        assertEquals("RESPONSE_TOO_LARGE", oversized.category());
        assertFalse(oversized.retryable());
        assertTrue(oversized.getMessage().contains("配置上限"));
        verify(transport).fetchWorks(any(), any(), any());
    }

    private SourceConnectionSettings settings(int retries) {
        return new SourceConnectionSettings(
                10, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), retries, 2 * 1024 * 1024);
    }

    private CrawlScope scope() {
        return new CrawlScope(null, null, "open access", List.of("A5048491430"), List.of(), 1, 100);
    }

    private byte[] fixture() throws IOException {
        try (var stream = getClass().getResourceAsStream("/openalex/work-page-sample.json")) {
            return java.util.Objects.requireNonNull(stream).readAllBytes();
        }
    }
}
