package com.aacv.system.source.infrastructure.crossref;

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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CrossrefDataSourceAdapterTests {

    private CrossrefHttpTransport transport;
    private AtomicReference<Duration> slept;
    private CrossrefDataSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        transport = mock(CrossrefHttpTransport.class);
        slept = new AtomicReference<>();
        Clock clock = Clock.fixed(Instant.parse("2026-09-02T01:00:00Z"), ZoneOffset.UTC);
        adapter = new CrossrefDataSourceAdapter(
                transport,
                new CrossrefResponseParser(new ObjectMapper(), clock),
                clock,
                slept::set);
    }

    @Test
    void retryableStatusHonorsRetryAfterAndCountsRequests() throws IOException {
        when(transport.fetchWorks(any(), any(), any())).thenReturn(
                new CrossrefHttpResponse(429, new byte[0], Duration.ofSeconds(2), Map.of()),
                new CrossrefHttpResponse(200, fixture(), null, Map.of("X-Concurrency-Limit", "1")));

        SourcePage page = adapter.fetchPage(settings(2), scope(), OpaqueCursor.first());

        assertEquals(2, page.requestCount());
        assertEquals(Duration.ofSeconds(2), slept.get());
        assertEquals(2, page.records().size());
        verify(transport, times(2)).fetchWorks(any(), any(), any());
    }

    @Test
    void nonRetryableStatusAndInvalidJsonFailWithoutExtraRequests() {
        when(transport.fetchWorks(any(), any(), any())).thenReturn(
                new CrossrefHttpResponse(400, new byte[0], null, Map.of()));
        CrossrefClientException badRequest = assertThrows(
                CrossrefClientException.class,
                () -> adapter.fetchPage(settings(3), scope(), OpaqueCursor.first()));
        assertEquals("HTTP_400", badRequest.category());
        assertFalse(badRequest.retryable());
        verify(transport).fetchWorks(any(), any(), any());

        org.mockito.Mockito.reset(transport);
        when(transport.fetchWorks(any(), any(), any())).thenReturn(
                new CrossrefHttpResponse(200, "not-json".getBytes(), null, Map.of()));
        assertThrows(
                CrossrefClientException.class,
                () -> adapter.fetchPage(settings(3), scope(), OpaqueCursor.first()));
        verify(transport).fetchWorks(any(), any(), any());
    }

    @Test
    void forbiddenIsNotRetriedAndSafeErrorDoesNotExposeRequestData() {
        when(transport.fetchWorks(any(), any(), any())).thenReturn(
                new CrossrefHttpResponse(403, new byte[0], null, Map.of()));

        CrossrefClientException exception = assertThrows(
                CrossrefClientException.class,
                () -> adapter.fetchPage(settings(3), scope(), new OpaqueCursor("secret-cursor")));

        assertEquals("HTTP_403", exception.category());
        assertFalse(exception.retryable());
        assertFalse(exception.getMessage().contains("secret-cursor"));
        assertFalse(exception.getMessage().contains("@"));
        verify(transport).fetchWorks(any(), any(), any());
    }

    @Test
    void temporaryServerStatusesAreRetriedWithinConfiguredBound() throws IOException {
        for (int status : List.of(502, 503, 504)) {
            org.mockito.Mockito.reset(transport);
            when(transport.fetchWorks(any(), any(), any())).thenReturn(
                    new CrossrefHttpResponse(status, new byte[0], null, Map.of()),
                    new CrossrefHttpResponse(200, fixture(), null, Map.of()));

            SourcePage page = adapter.fetchPage(settings(1), scope(), OpaqueCursor.first());

            assertEquals(2, page.requestCount());
            assertTrue(slept.get().toMillis() > 0);
            verify(transport, times(2)).fetchWorks(any(), any(), any());
        }
    }

    @Test
    void requiresConcurrencyOneClosedPublicationWindowAndValidIdentifiers() {
        CrawlScope halfOpen = new CrawlScope(
                LocalDate.of(2026, 1, 1), null, null, List.of(), List.of(), 1, 10);
        assertFalse(adapter.validate(settings(0), halfOpen).valid());

        SourceConnectionSettings concurrent = new SourceConnectionSettings(
                1, 2, Duration.ofSeconds(5), Duration.ofSeconds(30), 0, 1024 * 1024);
        assertFalse(adapter.validate(concurrent, scope()).valid());

        CrawlScope invalidIds = new CrawlScope(
                null, null, null, List.of(), List.of(), List.of("not-a-doi"),
                List.of("bad-orcid"), List.of("bad-ror"), null, null, 1, 10);
        assertFalse(adapter.validate(settings(0), invalidIds).valid());
    }

    @Test
    void declaresCrossrefParserAndClosedIndexCapabilities() {
        assertEquals("crossref-v1", adapter.parserVersion());
        assertEquals(
                com.aacv.system.source.domain.SourceCapabilities.IncrementalMode.CLOSED_INDEX_DATE_WINDOW,
                adapter.capabilities().incrementalMode());
        assertEquals(100, adapter.capabilities().maxPageSize());
    }

    private SourceConnectionSettings settings(int retries) {
        return new SourceConnectionSettings(
                1, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), retries, 2 * 1024 * 1024);
    }

    private CrawlScope scope() {
        return new CrawlScope(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                "graph learning",
                List.of(),
                List.of(),
                List.of("10.1000/example"),
                List.of("0000-0003-1613-5981"),
                List.of("https://ror.org/03yrm5c26"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T23:59:59Z"),
                5,
                500);
    }

    private byte[] fixture() throws IOException {
        try (var stream = getClass().getResourceAsStream("/crossref/work-page-sample.json")) {
            return java.util.Objects.requireNonNull(stream).readAllBytes();
        }
    }
}
