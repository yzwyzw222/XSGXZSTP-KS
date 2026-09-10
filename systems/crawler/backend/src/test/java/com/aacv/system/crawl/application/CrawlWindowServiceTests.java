package com.aacv.system.crawl.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.domain.*;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.source.domain.SourceType;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrawlWindowServiceTests {
    private final CrawlRepository repository = mock(CrawlRepository.class);
    private final Instant start = Instant.parse("2026-09-01T00:00:00Z");
    private final CrawlWindowService service = new CrawlWindowService(repository,
            Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void indexWindowFreezesBoundsAndRetainsAllOtherFilters() {
        CrawlTask task = task(true);
        CrawlWindow window = service.prepare(task, SourceType.CROSSREF, CrawlWindowService.INDEX).orElseThrow();
        assertEquals(start, window.start());
        assertEquals(start.plus(Duration.ofDays(7)), window.end());
        assertEquals(window.start(), window.scope().updatedFrom());
        assertEquals(window.end(), window.scope().updatedUntil());
        assertEquals(task.scope().dois(), window.scope().dois());
        assertEquals(500, window.scope().maxRecords());
    }

    @Test
    void onlyFullySuccessfulWindowAdvances() {
        previous(CrawlRunStatus.SUCCEEDED, CrawlCompletionReason.SOURCE_EXHAUSTED, 0, Duration.ofDays(7));
        CrawlWindow next = service.prepare(task(true), SourceType.CROSSREF, CrawlWindowService.INDEX).orElseThrow();
        assertEquals(start.plus(Duration.ofDays(7)), next.start());
        assertEquals(Instant.parse("2026-09-10T11:55:00Z"), next.end());
    }

    @Test
    void rowFailuresAndExecutionFailuresReplaySameWindow() {
        for (CrawlCompletionReason reason : List.of(CrawlCompletionReason.SOURCE_EXHAUSTED, CrawlCompletionReason.BATCH_FAILED)) {
            previous(CrawlRunStatus.PARTIAL_SUCCESS, reason, 1, Duration.ofDays(7));
            CrawlWindow next = service.prepare(task(true), SourceType.CROSSREF, CrawlWindowService.INDEX).orElseThrow();
            assertEquals(start, next.start());
            assertEquals(start.plus(Duration.ofDays(7)), next.end());
        }
    }

    @Test
    void limitsShrinkWindowAndMinimumWindowRemainsIncomplete() {
        previous(CrawlRunStatus.PARTIAL_SUCCESS, CrawlCompletionReason.RECORD_LIMIT, 0, Duration.ofDays(2));
        assertEquals(start.plus(Duration.ofDays(1)),
                service.prepare(task(true), SourceType.CROSSREF, CrawlWindowService.INDEX).orElseThrow().end());
        previous(CrawlRunStatus.PARTIAL_SUCCESS, CrawlCompletionReason.PAGE_LIMIT, 0, Duration.ofSeconds(1));
        assertThrows(ResourceConflictException.class,
                () -> service.prepare(task(true), SourceType.CROSSREF, CrawlWindowService.INDEX));
    }

    @Test
    void pausedWindowCannotBeSkippedAndModeMustMatchSource() {
        previous(CrawlRunStatus.PAUSED, CrawlCompletionReason.USER_PAUSED, 0, Duration.ofDays(7));
        assertThrows(ResourceConflictException.class,
                () -> service.prepare(task(true), SourceType.CROSSREF, CrawlWindowService.INDEX));
        assertThrows(IllegalArgumentException.class,
                () -> service.prepare(task(false), SourceType.OPENALEX, CrawlWindowService.INDEX));
        assertTrue(service.prepare(task(false), SourceType.OPENALEX, CrawlWindowService.FIXED).isEmpty());
    }

    @Test
    void publicationWindowUsesInclusiveDatesAndExcludesToday() {
        CrawlWindow window = service.prepare(task(false), SourceType.OPENALEX, CrawlWindowService.PUBLICATION).orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 1), window.scope().publicationDateFrom());
        assertEquals(LocalDate.of(2026, 9, 7), window.scope().publicationDateTo());
        assertNull(window.scope().updatedFrom());
    }

    private CrawlTask task(boolean crossref) {
        CrawlScope scope = new CrawlScope(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 9), "graph",
                List.of(), List.of(), crossref ? List.of("10.1000/test") : List.of(), List.of(), List.of(),
                crossref ? start : null, crossref ? start.plus(Duration.ofDays(1)) : null, 5, 500);
        return new CrawlTask(10, 1, "窗口测试", scope, crossref ? 2 : 1, "a".repeat(64), true, 0, 7, start, start);
    }

    private void previous(CrawlRunStatus status, CrawlCompletionReason reason, long failures, Duration width) {
        when(repository.findLatestWindowRun(10, CrawlWindowService.INDEX)).thenReturn(Optional.of(20L));
        when(repository.findRunById(20)).thenReturn(Optional.of(new CrawlRun(20, 10, "run-20",
                CrawlTriggerType.MANUAL, null, status, 20L, 100, 100, 100, 0, 0, failures,
                1, "end", start, start.plus(width), 0, reason, null, 0)));
        when(repository.findRunWindow(20)).thenReturn(Optional.of(
                new CrawlWindow(CrawlWindowService.INDEX, start, start.plus(width), task(true).scope())));
    }
}
