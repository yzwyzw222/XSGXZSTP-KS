package com.aacv.system.crawl.infrastructure.batch;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.domain.CrawlCheckpointState;
import com.aacv.system.crawl.domain.CrawlControlIntent;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.crawl.domain.CrawlTriggerType;
import com.aacv.system.crawl.domain.CrawlCompletionReason;
import com.aacv.system.ingestion.application.IngestionPageService;
import com.aacv.system.ingestion.application.port.IngestionRepository;
import com.aacv.system.ingestion.domain.RetryFailureRecord;
import com.aacv.system.source.application.DataSourceAdapterRegistry;
import com.aacv.system.source.application.port.DataSourceAdapter;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourcePage;
import java.util.List;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

class OpenAlexPageItemReader implements ItemStreamReader<CrawlPageItem> {

    private static final int MAX_RETRY_FAILURES = 100;

    private final long runId;
    private final CrawlRepository crawlRepository;
    private final DataSourceRepository sourceRepository;
    private final DataSourceAdapterRegistry adapterRegistry;
    private final IngestionRepository ingestionRepository;

    private DataSourceConfiguration source;
    private CrawlScope scope;
    private DataSourceAdapter adapter;
    private String cursor;
    private int committedPages;
    private long committedRecords;
    private Long expectedTotalResults;
    private boolean finished;
    private List<RetryFailureRecord> retryFailures = List.of();
    private int retryIndex;

    OpenAlexPageItemReader(
            long runId,
            CrawlRepository crawlRepository,
            DataSourceRepository sourceRepository,
            DataSourceAdapterRegistry adapterRegistry,
            IngestionRepository ingestionRepository) {
        this.runId = runId;
        this.crawlRepository = crawlRepository;
        this.sourceRepository = sourceRepository;
        this.adapterRegistry = adapterRegistry;
        this.ingestionRepository = ingestionRepository;
    }

    @Override
    public void open(ExecutionContext executionContext) {
        CrawlRun run = crawlRepository.findRunById(runId).orElseThrow();
        CrawlTask task = crawlRepository.findTaskById(run.taskId()).orElseThrow();
        source = sourceRepository.findById(task.sourceId()).orElseThrow();
        if (!source.enabled() || !task.enabled()) {
            throw new IllegalStateException("任务或数据源已停用，不能启动采集");
        }
        scope = task.scope();
        if (run.triggerType() == CrawlTriggerType.RETRY_FAILURES) {
            retryFailures = ingestionRepository.findRetryableFailures(
                    run.parentRunId(), Math.min(MAX_RETRY_FAILURES, scope.maxRecords()));
            retryIndex = 0;
            finished = retryFailures.isEmpty();
            return;
        }
        adapter = adapterRegistry.require(source.sourceType());
        CrawlCheckpointState checkpoint = crawlRepository.findCheckpoint(runId).orElse(null);
        cursor = checkpoint == null ? OpaqueCursor.FIRST_VALUE : checkpoint.cursor();
        committedPages = checkpoint == null ? 0 : checkpoint.committedPages();
        committedRecords = checkpoint == null ? 0 : checkpoint.committedRecords();
        finished = IngestionPageService.TERMINAL_CURSOR.equals(cursor)
                || committedPages >= scope.maxPages()
                || committedRecords >= scope.maxRecords();
    }

    @Override
    public CrawlPageItem read() {
        if (finished || crawlRepository.findControlIntent(runId) != null) {
            return null;
        }
        if (!retryFailures.isEmpty()) {
            RetryFailureRecord retry = retryFailures.get(retryIndex++);
            finished = retryIndex >= retryFailures.size();
            return new CrawlPageItem(
                    new SourcePage(List.of(retry.rawRecord()), null, 0, java.util.Map.of()),
                    retry.failureId(), finished ? CrawlCompletionReason.RETRY_BATCH_COMPLETED : null);
        }
        SourcePage fetched = adapter.fetchPage(source.settings(), scope, new OpaqueCursor(cursor));
        reconcileTotalResults(fetched);
        long remaining = scope.maxRecords() - committedRecords;
        List<com.aacv.system.ingestion.domain.RawSourceRecord> records = fetched.records();
        boolean truncated = records.size() > remaining;
        if (records.size() > remaining) {
            records = List.copyOf(records.subList(0, Math.toIntExact(remaining)));
        }
        SourcePage bounded = new SourcePage(
                records, fetched.nextCursor(), fetched.requestCount(), fetched.responseMetadata());
        committedPages++;
        committedRecords += records.size();
        cursor = fetched.nextCursor() == null
                ? IngestionPageService.TERMINAL_CURSOR
                : fetched.nextCursor().value();
        finished = IngestionPageService.TERMINAL_CURSOR.equals(cursor)
                || committedPages >= scope.maxPages()
                || committedRecords >= scope.maxRecords();
        CrawlCompletionReason reason = null;
        if (truncated) reason = CrawlCompletionReason.RECORD_LIMIT;
        else if (IngestionPageService.TERMINAL_CURSOR.equals(cursor)) reason = CrawlCompletionReason.SOURCE_EXHAUSTED;
        else if (committedRecords >= scope.maxRecords()) reason = CrawlCompletionReason.RECORD_LIMIT;
        else if (committedPages >= scope.maxPages()) reason = CrawlCompletionReason.PAGE_LIMIT;
        return new CrawlPageItem(bounded, null, reason);
    }

    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putString("source.cursor", cursor);
        executionContext.putInt("source.committedPages", committedPages);
        executionContext.putLong("source.committedRecords", committedRecords);
        executionContext.putInt("source.retryIndex", retryIndex);
        if (expectedTotalResults != null) {
            executionContext.putLong("source.expectedTotalResults", expectedTotalResults);
        }
    }

    private void reconcileTotalResults(SourcePage page) {
        String value = page.responseMetadata().get("message.total-results");
        if (value == null) {
            return;
        }
        long currentTotal;
        try {
            currentTotal = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("来源返回了无效的total-results", exception);
        }
        if (currentTotal < 0 || (expectedTotalResults != null && expectedTotalResults != currentTotal)) {
            throw new IllegalStateException("游标链期间Crossref total-results发生变化");
        }
        if (committedRecords + page.records().size() > currentTotal) {
            throw new IllegalStateException("Crossref页面记录数超过total-results");
        }
        expectedTotalResults = currentTotal;
    }
}
