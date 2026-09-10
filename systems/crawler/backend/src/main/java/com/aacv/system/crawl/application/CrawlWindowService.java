package com.aacv.system.crawl.application;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.domain.*;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.source.domain.SourceType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CrawlWindowService {
    public static final String FIXED = "FIXED_SCOPE_REFRESH";
    public static final String INDEX = "CLOSED_INDEX_DATE_WINDOW";
    public static final String PUBLICATION = "ROLLING_PUBLICATION_DATE_WINDOW";
    private final CrawlRepository repository;
    private final Clock clock;

    public CrawlWindowService(CrawlRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void validate(CrawlTask task, SourceType sourceType, String mode) {
        if (FIXED.equals(mode)) return;
        if (INDEX.equals(mode) && sourceType == SourceType.CROSSREF && task.scope().updatedFrom() != null) return;
        if (PUBLICATION.equals(mode) && sourceType == SourceType.OPENALEX && task.scope().publicationDateFrom() != null) return;
        throw new IllegalArgumentException("增量模式必须匹配数据源，并提供外部更新时间起或出版日期起");
    }

    /** 调用方持有任务锁；仅成功耗尽窗口的运行可以推进后续起点。 */
    public Optional<CrawlWindow> prepare(CrawlTask task, SourceType sourceType, String mode) {
        validate(task, sourceType, mode);
        if (FIXED.equals(mode)) return Optional.empty();
        boolean indexed = INDEX.equals(mode);
        Instant start = indexed ? task.scope().updatedFrom().truncatedTo(ChronoUnit.SECONDS)
                : task.scope().publicationDateFrom().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant cutoff = indexed ? clock.instant().minusSeconds(300).truncatedTo(ChronoUnit.SECONDS)
                : clock.instant().atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = null;
        Optional<Long> previousId = repository.findLatestWindowRun(task.id(), mode);
        if (previousId.isPresent()) {
            CrawlRun previous = repository.findRunById(previousId.get()).orElseThrow();
            CrawlWindow window = repository.findRunWindow(previous.id()).orElseThrow();
            if (!CrawlRunStateMachine.isTerminal(previous.status())) {
                throw new ResourceConflictException("该增量窗口仍在执行或暂停，请先完成当前运行");
            }
            start = window.start();
            end = window.end();
            if (previous.completionReason() == CrawlCompletionReason.SOURCE_EXHAUSTED && previous.failureCount() == 0) {
                start = end;
                end = null;
            } else if (previous.completionReason() != null && previous.completionReason().limited()) {
                long units = indexed ? Duration.between(start, end).getSeconds() : Duration.between(start, end).toDays();
                if (units <= 1) {
                    throw new ResourceConflictException("最小增量窗口仍超过采集上限，请增加关键词、作者或机构筛选后创建任务；当前窗口未标记完整");
                }
                end = start.plus(units / 2, indexed ? ChronoUnit.SECONDS : ChronoUnit.DAYS);
            }
        }
        if (!start.isBefore(cutoff)) throw new ResourceConflictException("暂时没有可采集的新窗口，请等待下次计划");
        if (end == null) end = start.plus(7, ChronoUnit.DAYS);
        if (end.isAfter(cutoff)) end = cutoff;
        CrawlScope base = task.scope();
        // 索引时间边界有意重叠，依靠幂等入库避免边界时间精度造成漏采。
        CrawlScope effective = new CrawlScope(
                indexed ? base.publicationDateFrom() : start.atZone(ZoneOffset.UTC).toLocalDate(),
                indexed ? base.publicationDateTo() : end.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1),
                base.keyword(), base.authorIds(), base.institutionIds(), base.dois(), base.orcids(), base.rorIds(),
                indexed ? start : null, indexed ? end : null, base.maxPages(), base.maxRecords());
        return Optional.of(new CrawlWindow(mode, start, end, effective));
    }
}
