package com.aacv.system.crawl.infrastructure.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.domain.CrawlCheckpointState;
import com.aacv.system.crawl.domain.CrawlControlIntent;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.crawl.domain.CrawlTriggerType;
import com.aacv.system.ingestion.application.port.IngestionRepository;
import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.source.application.DataSourceAdapterRegistry;
import com.aacv.system.source.application.port.DataSourceAdapter;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourcePage;
import com.aacv.system.source.domain.SourceType;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;

class OpenAlexPageItemReaderTests {

    private CrawlRepository crawlRepository;
    private DataSourceAdapter adapter;
    private OpenAlexPageItemReader reader;

    @BeforeEach
    void setUp() {
        crawlRepository = mock(CrawlRepository.class);
        DataSourceRepository sourceRepository = mock(DataSourceRepository.class);
        DataSourceAdapterRegistry registry = mock(DataSourceAdapterRegistry.class);
        adapter = mock(DataSourceAdapter.class);
        CrawlScope scope = new CrawlScope(null, null, null, List.of(), List.of(), 5, 500);
        CrawlTask task = new CrawlTask(
                10, 5, "reader-task", scope, 2, "a".repeat(64), true, 0, 7,
                Instant.EPOCH, Instant.EPOCH);
        CrawlRun run = new CrawlRun(
                1, 10, "reader-run", CrawlTriggerType.MANUAL, null,
                CrawlRunStatus.RUNNING, 11L,
                100, 100, 100, 0, 0, 0, 1, "cursor-2", Instant.EPOCH, null, 0);
        DataSourceConfiguration source = new DataSourceConfiguration(
                5,
                DataSourceConfiguration.CROSSREF_CODE,
                SourceType.CROSSREF,
                DataSourceConfiguration.CROSSREF_BASE_URI,
                true,
                new SourceConnectionSettings(
                        10, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 1, 1024 * 1024),
                "恢复测试固定Crossref来源", null, null, 0, 0, Instant.EPOCH, Instant.EPOCH);
        when(crawlRepository.findRunById(1)).thenReturn(Optional.of(run));
        when(crawlRepository.findTaskById(10)).thenReturn(Optional.of(task));
        when(crawlRepository.findCheckpoint(1)).thenReturn(Optional.of(
                new CrawlCheckpointState("cursor-2", 1, 0, 0)));
        when(sourceRepository.findById(5)).thenReturn(Optional.of(source));
        when(registry.require(SourceType.CROSSREF)).thenReturn(adapter);
        reader = new OpenAlexPageItemReader(
                1, crawlRepository, sourceRepository, registry, mock(IngestionRepository.class));
    }

    @Test
    void resumesFromLastCommittedOpaqueCursor() {
        when(adapter.fetchPage(any(), any(), any())).thenReturn(
                new SourcePage(List.of(), null, 1, Map.of()));
        reader.open(new ExecutionContext());

        CrawlPageItem item = reader.read();

        assertEquals(0, item.page().records().size());
        assertEquals(com.aacv.system.crawl.domain.CrawlCompletionReason.SOURCE_EXHAUSTED, item.completionReason());
        verify(adapter).fetchPage(any(), any(), org.mockito.ArgumentMatchers.eq(new OpaqueCursor("cursor-2")));
    }

    @Test
    void writesSourceNeutralCheckpointKeys() {
        when(adapter.fetchPage(any(), any(), any())).thenReturn(
                new SourcePage(List.of(), null, 1, Map.of()));
        ExecutionContext context = new ExecutionContext();
        reader.open(context);
        reader.read();

        reader.update(context);

        assertEquals("__END__", context.getString("source.cursor"));
        assertNull(context.get("openalex.cursor"));
    }

    @Test
    void rejectsChangedCrossrefTotalDuringCursorChain() {
        RawSourceRecord firstRecord = new RawSourceRecord(
                SourceType.CROSSREF,
                "10.1000/first",
                URI.create("https://api.crossref.org/works/10.1000%2Ffirst"),
                "{}",
                Instant.EPOCH);
        when(adapter.fetchPage(any(), any(), any())).thenReturn(
                new SourcePage(
                        List.of(firstRecord),
                        new OpaqueCursor("cursor-3"),
                        1,
                        Map.of("message.total-results", "2")),
                new SourcePage(
                        List.of(),
                        null,
                        1,
                        Map.of("message.total-results", "3")));
        reader.open(new ExecutionContext());

        reader.read();

        assertThrows(IllegalStateException.class, reader::read);
    }

    @Test
    void pauseOrCancelIntentStopsBeforeNextChunk() {
        when(crawlRepository.findControlIntent(1)).thenReturn(CrawlControlIntent.PAUSE);
        reader.open(new ExecutionContext());
        assertNull(reader.read());

        when(crawlRepository.findControlIntent(1)).thenReturn(CrawlControlIntent.CANCEL);
        assertNull(reader.read());
    }

    @Test
    void lastAllowedPageWithMoreCursorIsReportedAsPageLimit() {
        when(crawlRepository.findCheckpoint(1)).thenReturn(Optional.of(new CrawlCheckpointState("next", 4, 10, 0)));
        when(adapter.fetchPage(any(), any(), any())).thenReturn(new SourcePage(List.of(), new OpaqueCursor("more"), 1, Map.of()));
        reader.open(new ExecutionContext());
        assertEquals(com.aacv.system.crawl.domain.CrawlCompletionReason.PAGE_LIMIT, reader.read().completionReason());
        assertNull(reader.read());
    }

    @Test
    void truncatedTerminalPageDoesNotClaimSourceExhaustion() {
        when(crawlRepository.findCheckpoint(1)).thenReturn(Optional.of(new CrawlCheckpointState("next", 1, 499, 0)));
        RawSourceRecord record = new RawSourceRecord(SourceType.CROSSREF, "10.1000/first",
                URI.create("https://api.crossref.org/works/10.1000%2Ffirst"), "{}", Instant.EPOCH);
        when(adapter.fetchPage(any(), any(), any())).thenReturn(new SourcePage(List.of(record, record), null, 1, Map.of()));
        reader.open(new ExecutionContext());
        CrawlPageItem item = reader.read();
        assertEquals(1, item.page().records().size());
        assertEquals(com.aacv.system.crawl.domain.CrawlCompletionReason.RECORD_LIMIT, item.completionReason());
    }

    @Test
    void quotaFailureDoesNotAdvanceCheckpointOrConsumeRemainingLimit() {
        when(adapter.fetchPage(any(), any(), any())).thenThrow(
                new com.aacv.system.source.application.SourceQuotaExhaustedException(Instant.now().plusSeconds(60)));
        ExecutionContext context = new ExecutionContext();
        reader.open(context);
        assertThrows(com.aacv.system.source.application.SourceQuotaExhaustedException.class, reader::read);
        reader.update(context);
        assertEquals("cursor-2", context.getString("source.cursor"));
        assertEquals(1, context.getInt("source.committedPages"));
    }
}
