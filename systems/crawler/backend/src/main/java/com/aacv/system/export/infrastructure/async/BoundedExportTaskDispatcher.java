package com.aacv.system.export.infrastructure.async;

import com.aacv.system.export.application.ExportTaskProcessor;
import com.aacv.system.export.application.ExportTaskFinalizer;
import com.aacv.system.export.application.port.ExportTaskDispatcher;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
class BoundedExportTaskDispatcher implements ExportTaskDispatcher {

    private final ThreadPoolTaskExecutor executor;
    private final ExportTaskProcessor processor;
    private final ExportTaskFinalizer finalizer;

    BoundedExportTaskDispatcher(
            @Qualifier("exportTaskExecutor") ThreadPoolTaskExecutor executor,
            ExportTaskProcessor processor,
            ExportTaskFinalizer finalizer) {
        this.executor = executor;
        this.processor = processor;
        this.finalizer = finalizer;
    }

    @Override
    public void submit(String taskId) {
        try {
            executor.execute(() -> processor.process(taskId));
        } catch (RejectedExecutionException exception) {
            finalizer.fail(taskId, "EXPORT_CONCURRENCY_LIMIT", "导出执行队列已满", Instant.now());
        }
    }
}
