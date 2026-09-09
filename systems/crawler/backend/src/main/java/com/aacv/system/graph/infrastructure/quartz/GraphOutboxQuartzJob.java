package com.aacv.system.graph.infrastructure.quartz;

import com.aacv.system.graph.application.GraphOutboxProcessor;
import com.aacv.system.graph.infrastructure.neo4j.GraphSchemaInitializer;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class GraphOutboxQuartzJob implements Job {

    @Autowired
    private GraphOutboxProcessor processor;

    @Autowired
    private GraphSchemaInitializer schemaInitializer;

    @Override
    public void execute(JobExecutionContext context) {
        if (schemaInitializer.ensureReady()) {
            processor.processBatch();
        }
    }
}
