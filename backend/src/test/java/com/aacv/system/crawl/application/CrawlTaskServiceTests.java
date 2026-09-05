package com.aacv.system.crawl.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlScopeCodec;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import com.aacv.system.crawl.domain.CrawlSchedule;
import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrawlTaskServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-02T01:00:00Z");
    private static final String HASH = "a".repeat(64);

    private CrawlRepository repository;
    private DataSourceRepository sourceRepository;
    private CrawlScopeCodec scopeCodec;
    private CrawlTaskService service;

    @BeforeEach
    void setUp() {
        repository = mock(CrawlRepository.class);
        sourceRepository = mock(DataSourceRepository.class);
        scopeCodec = mock(CrawlScopeCodec.class);
        CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
        when(actorProvider.currentUserId()).thenReturn(OptionalLong.of(7));
        when(scopeCodec.hash(any())).thenReturn(HASH);
        service = new CrawlTaskService(
                repository,
                sourceRepository,
                scopeCodec,
                actorProvider,
                mock(AuditService.class),
                mock(com.aacv.system.crawl.application.port.CrawlRunLaunchPort.class),
                mock(com.aacv.system.crawl.application.port.CrawlSchedulePort.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createNormalizesNameAndBindsTaskToEnabledSource() {
        CrawlScope scope = scope();
        when(sourceRepository.lockById(1)).thenReturn(Optional.of(source(true)));
        when(repository.insertTask(any())).thenAnswer(invocation -> {
            CrawlTask candidate = invocation.getArgument(0);
            return new CrawlTask(
                    10, candidate.sourceId(), candidate.name(), candidate.scope(), candidate.parameterVersion(),
                    candidate.parameterHash(),
                    candidate.enabled(), candidate.version(), candidate.createdBy(),
                    candidate.createdAt(), candidate.updatedAt());
        });

        CrawlTask created = service.create(1, "  OpenAlex每日任务  ", scope);

        assertEquals("OpenAlex每日任务", created.name());
        assertEquals(1, created.sourceId());
        assertEquals(HASH, created.parameterHash());
        assertEquals(7, created.createdBy());
        assertEquals(1, created.parameterVersion());
    }

    @Test
    void crossrefTaskUsesParameterVersionTwo() {
        CrawlScope crossrefScope = new CrawlScope(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 8, 31),
                null,
                List.of(),
                List.of(),
                List.of("10.1000/example"),
                List.of("0000-0003-1613-5981"),
                List.of("https://ror.org/03yrm5c26"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T23:59:59Z"),
                5,
                500);
        when(sourceRepository.lockById(2)).thenReturn(Optional.of(source(2, SourceType.CROSSREF, true)));
        when(repository.insertTask(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CrawlTask created = service.create(2, "Crossref任务", crossrefScope);

        assertEquals(2, created.parameterVersion());
    }

    @Test
    void openAlexTaskRejectsCrossrefOnlyFields() {
        CrawlScope invalidScope = new CrawlScope(
                null, null, null, List.of(), List.of(), List.of("10.1000/example"),
                List.of(), List.of(), null, null, 1, 1);
        when(sourceRepository.lockById(1)).thenReturn(Optional.of(source(true)));

        assertThrows(IllegalArgumentException.class, () -> service.create(1, "非法任务", invalidScope));

        verify(repository, never()).insertTask(any());
    }

    @Test
    void disabledSourceRejectsTaskCreationBeforeWrite() {
        when(sourceRepository.lockById(1)).thenReturn(Optional.of(source(false)));

        assertThrows(ResourceConflictException.class, () -> service.create(1, "任务", scope()));

        verify(repository, never()).insertTask(any());
    }

    @Test
    void taskWithRunCannotChangeCollectionScope() {
        when(repository.lockTaskById(10)).thenReturn(Optional.of(task(true)));
        when(repository.taskHasRuns(10)).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> service.update(10, "任务", scope(), 0));

        verify(repository, never()).updateTask(any(), any(Long.class));
    }

    @Test
    void activeRunWithSameSourceAndParametersRejectsDuplicateTrigger() {
        CrawlTask task = task(true);
        when(repository.lockTaskById(10)).thenReturn(Optional.of(task));
        when(sourceRepository.lockById(1)).thenReturn(Optional.of(source(true)));
        when(repository.hasActiveConflict(1, HASH)).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> service.trigger(10));

        verify(repository, never()).insertPendingRun(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void triggerCreatesPendingRunWhenNoConflictExists() {
        CrawlTask task = task(true);
        CrawlRun pending = new CrawlRun(
                20, 10, "run-20", com.aacv.system.crawl.domain.CrawlTriggerType.MANUAL, null,
                CrawlRunStatus.PENDING, null,
                0, 0, 0, 0, 0, 0, 0, null, null, null, 0);
        when(repository.lockTaskById(10)).thenReturn(Optional.of(task));
        when(sourceRepository.lockById(1)).thenReturn(Optional.of(source(true)));
        when(repository.insertPendingRun(any(), any(), org.mockito.ArgumentMatchers.eq(7L))).thenReturn(pending);

        CrawlRun created = service.trigger(10);

        assertEquals(CrawlRunStatus.PENDING, created.status());
        assertEquals(10, created.taskId());
    }

    @Test
    void dailyScheduleUsesExplicitZoneAndRollsPastTimeToNextDay() {
        when(repository.lockTaskById(10)).thenReturn(Optional.of(task(true)));
        when(sourceRepository.lockById(1)).thenReturn(Optional.of(source(true)));
        when(repository.saveSchedule(any(), org.mockito.ArgumentMatchers.isNull()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        CrawlSchedule schedule = service.configureDailySchedule(
                10, LocalTime.of(8, 0), ZoneId.of("Asia/Shanghai"), null);

        assertEquals(Instant.parse("2026-09-03T00:00:00Z"), schedule.nextFireAt());
        assertEquals(ZoneId.of("Asia/Shanghai"), schedule.timeZone());
        assertEquals("FIXED_SCOPE_REFRESH", schedule.incrementalMode());
    }

    @Test
    void crossrefDailyScheduleTruthfullyReportsFixedScopeRefresh() {
        CrawlTask crossrefTask = new CrawlTask(
                10, 2, "Crossref任务", scope(), 2, HASH, true, 0, 7, NOW, NOW);
        when(repository.lockTaskById(10)).thenReturn(Optional.of(crossrefTask));
        when(sourceRepository.lockById(2)).thenReturn(Optional.of(source(2, SourceType.CROSSREF, true)));
        when(repository.saveSchedule(any(), org.mockito.ArgumentMatchers.isNull()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        CrawlSchedule schedule = service.configureDailySchedule(
                10, LocalTime.of(8, 0), ZoneId.of("Asia/Shanghai"), null);

        assertEquals("FIXED_SCOPE_REFRESH", schedule.incrementalMode());
    }

    @Test
    void scopeBudgetsEnforceStageThreeAcceptanceLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CrawlScope(null, null, null, List.of(), List.of(), 6, 500));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CrawlScope(null, null, null, List.of(), List.of(), 5, 501));
    }

    private CrawlScope scope() {
        return new CrawlScope(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 8, 31),
                "graph learning",
                List.of("A1"),
                List.of("I1"),
                5,
                500);
    }

    private CrawlTask task(boolean enabled) {
        return new CrawlTask(10, 1, "任务", scope(), HASH, enabled, 0, 7, NOW, NOW);
    }

    private DataSourceConfiguration source(boolean enabled) {
        return source(1, SourceType.OPENALEX, enabled);
    }

    private DataSourceConfiguration source(long id, SourceType sourceType, boolean enabled) {
        return new DataSourceConfiguration(
                id,
                DataSourceConfiguration.sourceCode(sourceType),
                sourceType,
                DataSourceConfiguration.baseUri(sourceType),
                enabled,
                new SourceConnectionSettings(
                        2, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 2, 2 * 1024 * 1024),
                "合规说明",
                null,
                null,
                0,
                0,
                NOW,
                NOW);
    }
}
