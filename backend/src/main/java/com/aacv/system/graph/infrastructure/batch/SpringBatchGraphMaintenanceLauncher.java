package com.aacv.system.graph.infrastructure.batch;

import com.aacv.system.graph.application.GraphMaintenanceStateService;
import com.aacv.system.graph.application.port.GraphMaintenanceLaunchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class SpringBatchGraphMaintenanceLauncher implements GraphMaintenanceLaunchPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringBatchGraphMaintenanceLauncher.class);
    private final JobOperator jobOperator;
    private final Job job;
    private final ObjectProvider<GraphMaintenanceStateService> stateServiceProvider;

    SpringBatchGraphMaintenanceLauncher(
            @Qualifier("crawlJobOperator") JobOperator jobOperator,
            @Qualifier("graphMaintenanceJob") Job job,
            ObjectProvider<GraphMaintenanceStateService> stateServiceProvider) {
        this.jobOperator = jobOperator;
        this.job = job;
        this.stateServiceProvider = stateServiceProvider;
    }

    @Override
    public void launchAfterCommit(long runId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    launch(runId);
                }
            });
        } else {
            launch(runId);
        }
    }

    private void launch(long runId) {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("runId", runId)
                .addLong("launchEpoch", System.currentTimeMillis())
                .toJobParameters();
        try {
            jobOperator.start(job, parameters);
        } catch (Exception exception) {
            LOGGER.error("Spring Batch图维护启动失败，runId={}，异常类型={}",
                    runId, exception.getClass().getSimpleName());
            stateServiceProvider.getObject().markFailed(
                    runId, "GRAPH_MAINTENANCE_LAUNCH_FAILED", "图维护Batch启动失败");
        }
    }
}
