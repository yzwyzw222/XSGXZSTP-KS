package com.aacv.system.crawl.application;

import com.aacv.system.crawl.application.port.CrawlRepository;
import com.aacv.system.crawl.application.port.CrawlRunLaunchPort;
import com.aacv.system.crawl.application.port.CrawlSchedulePort;
import com.aacv.system.crawl.domain.CrawlRecoveryCandidate;
import com.aacv.system.crawl.domain.CrawlRunStatus;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CrawlRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CrawlRecoveryService.class);
    private static final Set<String> INTERRUPTED_BATCH_STATUSES = Set.of("STARTING", "STARTED", "STOPPING");

    private final CrawlRepository repository;
    private final CrawlRunService runService;
    private final CrawlRunLaunchPort launchPort;
    private final CrawlSchedulePort schedulePort;

    public CrawlRecoveryService(
            CrawlRepository repository,
            CrawlRunService runService,
            CrawlRunLaunchPort launchPort,
            CrawlSchedulePort schedulePort) {
        this.repository = repository;
        this.runService = runService;
        this.launchPort = launchPort;
        this.schedulePort = schedulePort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileAfterRestart() {
        repository.findEnabledSchedules().forEach(schedulePort::synchronizeAfterCommit);
        for (CrawlRecoveryCandidate candidate : repository.findRecoveryCandidates()) {
            reconcile(candidate);
        }
    }

    void reconcile(CrawlRecoveryCandidate candidate) {
        if (candidate.businessStatus() == CrawlRunStatus.PAUSING) {
            runService.completeBatch(candidate.runId(), true);
            return;
        }
        if (candidate.businessStatus() == CrawlRunStatus.CANCELLING) {
            runService.completeBatch(candidate.runId(), true);
            return;
        }
        if (candidate.businessStatus() == CrawlRunStatus.RUNNING
                && candidate.batchStatus() != null
                && INTERRUPTED_BATCH_STATUSES.contains(candidate.batchStatus())) {
            LOGGER.warn(
                    "检测到可恢复的中断采集运行，runId={}，checkpointPresent={}",
                    candidate.runId(),
                    candidate.checkpointPresent());
            launchPort.launchAfterCommit(candidate.runId());
            return;
        }
        LOGGER.error(
                "Batch元数据与业务运行状态不一致，停止自动推进，runId={}，businessStatus={}，batchStatus={}",
                candidate.runId(),
                candidate.businessStatus(),
                candidate.batchStatus());
        runService.completeBatch(candidate.runId(), false);
    }
}
