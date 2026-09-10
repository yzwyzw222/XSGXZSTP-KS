package com.aacv.system.crawl.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlRunLaunchPort;
import com.aacv.system.crawl.domain.CrawlControlIntent;
import com.aacv.system.crawl.domain.CrawlRun;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import com.aacv.system.crawl.domain.CrawlTriggerType;
import com.aacv.system.crawl.domain.CrawlTask;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.application.port.CurrentActorProvider;
import com.aacv.system.shared.application.ResourceConflictException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrawlRunServiceTests {

    private CrawlRepository repository;
    private CrawlRunLaunchPort launchPort;
    private CrawlRunService service;
    private com.aacv.system.source.application.port.DataSourceRepository sourceRepository;

    @BeforeEach
    void setUp() {
        repository = mock(CrawlRepository.class);
        launchPort = mock(CrawlRunLaunchPort.class);
        CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
        when(actorProvider.currentUserId()).thenReturn(java.util.OptionalLong.of(7));
        sourceRepository = mock(com.aacv.system.source.application.port.DataSourceRepository.class);
        service = new CrawlRunService(repository, launchPort, mock(AuditService.class), actorProvider,
                java.time.Clock.systemUTC(), sourceRepository);
    }

    @Test
    void pauseSetsIntentAndWaitsForChunkBoundary() {
        CrawlRun running = run(CrawlRunStatus.RUNNING, 0);
        CrawlRun pausing = run(CrawlRunStatus.PAUSING, 0);
        when(repository.lockRunById(1)).thenReturn(Optional.of(running));
        when(repository.transitionRun(
                        1,
                        CrawlRunStatus.RUNNING,
                        CrawlRunStatus.PAUSING,
                        CrawlControlIntent.PAUSE,
                        null,
                        false,
                        false))
                .thenReturn(Optional.of(pausing));

        assertEquals(CrawlRunStatus.PAUSING, service.requestPause(1).status());
    }

    @Test
    void resumeReusesBusinessRunAndLaunchesAfterTransition() {
        CrawlRun paused = run(CrawlRunStatus.PAUSED, 0);
        CrawlRun running = run(CrawlRunStatus.RUNNING, 0);
        when(repository.lockRunById(1)).thenReturn(Optional.of(paused));
        when(repository.transitionRun(
                        1,
                        CrawlRunStatus.PAUSED,
                        CrawlRunStatus.RUNNING,
                        null,
                        null,
                        false,
                        false))
                .thenReturn(Optional.of(running));

        service.resume(1);

        verify(launchPort).launchAfterCommit(1);
    }

    @Test
    void completedRunCannotBePausedOrResumed() {
        when(repository.lockRunById(1)).thenReturn(Optional.of(run(CrawlRunStatus.SUCCEEDED, 0)));

        assertThrows(ResourceConflictException.class, () -> service.requestPause(1));
    }

    @Test
    void successfulBatchWithRowFailuresBecomesPartialSuccess() {
        CrawlRun running = run(CrawlRunStatus.RUNNING, 2);
        CrawlRun partial = run(CrawlRunStatus.PARTIAL_SUCCESS, 2);
        when(repository.lockRunById(1)).thenReturn(Optional.of(running));
        when(repository.transitionRun(
                        1,
                        CrawlRunStatus.RUNNING,
                        CrawlRunStatus.PARTIAL_SUCCESS,
                        null,
                        null,
                        false,
                        true))
                .thenReturn(Optional.of(partial));

        assertEquals(CrawlRunStatus.PARTIAL_SUCCESS, service.completeBatch(1, true).status());
    }

    @Test
    void pendingCancellationFinishesWithoutStartingBatch() {
        CrawlRun pending = run(CrawlRunStatus.PENDING, 0);
        CrawlRun cancelled = run(CrawlRunStatus.CANCELLED, 0);
        when(repository.lockRunById(1)).thenReturn(Optional.of(pending));
        when(repository.transitionRun(
                        1,
                        CrawlRunStatus.PENDING,
                        CrawlRunStatus.CANCELLED,
                        null,
                        null,
                        false,
                        true))
                .thenReturn(Optional.of(cancelled));

        assertEquals(CrawlRunStatus.CANCELLED, service.requestCancel(1).status());
    }

    @Test
    void retryFailuresCreatesTraceableBoundedChildRun() {
        CrawlRun parent = run(CrawlRunStatus.PARTIAL_SUCCESS, 1);
        CrawlTask task = mock(CrawlTask.class);
        CrawlRun retry = new CrawlRun(
                2, 10, "retry-2", CrawlTriggerType.RETRY_FAILURES, 1L,
                CrawlRunStatus.PENDING, null,
                0, 0, 0, 0, 0, 0, 0, null, null, null, 0);
        when(repository.lockRunById(1)).thenReturn(Optional.of(parent));
        when(repository.countRetryableFailures(1, 100)).thenReturn(1);
        when(repository.lockTaskById(10)).thenReturn(Optional.of(task));
        when(task.sourceId()).thenReturn(5L);
        when(task.parameterHash()).thenReturn("hash");
        when(repository.insertPendingRun(any(), any(),
                org.mockito.ArgumentMatchers.eq("RETRY_FAILURES"),
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(1L))).thenReturn(retry);

        CrawlRun created = service.retryFailures(1);

        assertEquals(1L, created.parentRunId());
        assertEquals(CrawlTriggerType.RETRY_FAILURES, created.triggerType());
        verify(launchPort).launchAfterCommit(2);
    }

    private CrawlRun run(CrawlRunStatus status, long failureCount) {
        return new CrawlRun(
                1,
                10,
                "run-1",
                CrawlTriggerType.MANUAL,
                null,
                status,
                null,
                0,
                0,
                0,
                0,
                0,
                failureCount,
                0,
                null,
                null,
                null,
                0);
    }

    @Test
    void checkpointRetryLocksSourceBeforeCheckingConflicts() {
        CrawlRun failed = new CrawlRun(1, 10, "run-1", CrawlTriggerType.MANUAL, null, CrawlRunStatus.FAILED, 11L,
                10, 10, 10, 0, 0, 0, 1, "next", null, null, 0,
                com.aacv.system.crawl.domain.CrawlCompletionReason.BATCH_FAILED, null, 0);
        CrawlTask task = mock(CrawlTask.class);
        var source = mock(com.aacv.system.source.domain.DataSourceConfiguration.class);
        when(repository.lockRunById(1)).thenReturn(Optional.of(failed));
        when(repository.lockTaskById(10)).thenReturn(Optional.of(task));
        when(task.sourceId()).thenReturn(5L);
        when(task.parameterHash()).thenReturn("hash");
        when(task.enabled()).thenReturn(true);
        when(source.enabled()).thenReturn(true);
        when(sourceRepository.lockById(5)).thenReturn(Optional.of(source));
        when(repository.hasActiveConflict(5, "hash")).thenReturn(true);
        assertThrows(ResourceConflictException.class, () -> service.retryRun(1));
        var order = org.mockito.Mockito.inOrder(sourceRepository, repository);
        order.verify(sourceRepository).lockById(5);
        order.verify(repository).hasActiveConflict(5, "hash");
        verify(launchPort, org.mockito.Mockito.never()).launchAfterCommit(anyLong());
    }

    @Test
    void failedLaunchPersistsSystemFailureAndDoesNotDuplicateTerminalRecords() {
        var failure = com.aacv.system.crawl.domain.CrawlLaunchFailure.EXECUTOR_BUSY;
        when(repository.lockRunById(1)).thenReturn(Optional.of(run(CrawlRunStatus.PENDING, 0)));
        when(repository.transitionRun(1, CrawlRunStatus.PENDING, CrawlRunStatus.FAILED, null, null, false, true))
                .thenReturn(Optional.of(run(CrawlRunStatus.FAILED, 0)));
        assertEquals(CrawlRunStatus.FAILED, service.failLaunch(1, failure).status());
        verify(repository).recordLaunchFailure(1, failure);
        when(repository.lockRunById(1)).thenReturn(Optional.of(run(CrawlRunStatus.FAILED, 0)));
        service.failLaunch(1, failure);
        verify(repository, org.mockito.Mockito.times(1)).recordLaunchFailure(1, failure);
    }
}
