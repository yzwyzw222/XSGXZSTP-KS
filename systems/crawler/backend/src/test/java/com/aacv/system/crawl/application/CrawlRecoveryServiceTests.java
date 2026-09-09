package com.aacv.system.crawl.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlRunLaunchPort;
import com.aacv.system.crawl.application.port.CrawlSchedulePort;
import com.aacv.system.crawl.domain.CrawlRecoveryCandidate;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrawlRecoveryServiceTests {

    private CrawlRunService runService;
    private CrawlRunLaunchPort launchPort;
    private CrawlRecoveryService service;

    @BeforeEach
    void setUp() {
        runService = mock(CrawlRunService.class);
        launchPort = mock(CrawlRunLaunchPort.class);
        service = new CrawlRecoveryService(
                mock(CrawlRepository.class),
                runService,
                launchPort,
                mock(CrawlSchedulePort.class));
    }

    @Test
    void interruptedRunningExecutionIsRelaunchedFromBusinessCheckpoint() {
        service.reconcile(new CrawlRecoveryCandidate(
                1, CrawlRunStatus.RUNNING, 11L, "STARTED", true));

        verify(launchPort).launchAfterCommit(1);
    }

    @Test
    void restartFinalizesPauseAndCancellationIntents() {
        service.reconcile(new CrawlRecoveryCandidate(
                2, CrawlRunStatus.PAUSING, 12L, "STOPPED", true));
        service.reconcile(new CrawlRecoveryCandidate(
                3, CrawlRunStatus.CANCELLING, 13L, "STOPPED", true));

        verify(runService).completeBatch(2, true);
        verify(runService).completeBatch(3, true);
    }

    @Test
    void inconsistentMetadataFailsBusinessRunInsteadOfAdvancing() {
        service.reconcile(new CrawlRecoveryCandidate(
                4, CrawlRunStatus.RUNNING, 14L, "COMPLETED", true));

        verify(runService).completeBatch(4, false);
    }
}
