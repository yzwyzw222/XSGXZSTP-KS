package com.aacv.system.ingestion.infrastructure.retention;

import com.aacv.system.ingestion.application.RawPayloadRetentionService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.quartz.DisallowConcurrentExecution;

@DisallowConcurrentExecution
public class RawPayloadRetentionJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(RawPayloadRetentionJob.class);
    private static final int BATCH_SIZE = 500;

    @Autowired
    private RawPayloadRetentionService service;

    @Override
    public void execute(JobExecutionContext context) {
        RawPayloadRetentionService.CleanupResult result = service.cleanupExpired(BATCH_SIZE);
        LOGGER.info("原始Payload保留清理完成，batchSize={}，clearedCount={}",
                result.batchSize(), result.clearedCount());
    }
}
