package com.aacv.system.export.application;

import com.aacv.system.export.application.port.ExportRepository;
import com.aacv.system.export.application.port.ExportTaskDispatcher;
import com.aacv.system.export.infrastructure.config.ExportProperties;
import java.time.Instant;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class ExportRecoveryService {

    private final ExportRepository repository;
    private final ExportTaskDispatcher dispatcher;
    private final ExportTaskFinalizer finalizer;
    private final ExportProperties properties;

    ExportRecoveryService(
            ExportRepository repository,
            ExportTaskDispatcher dispatcher,
            ExportTaskFinalizer finalizer,
            ExportProperties properties) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.finalizer = finalizer;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int capacity = properties.getConcurrency() + properties.getQueueCapacity();
        repository.findRunningIds().forEach(taskId -> finalizer.fail(
                taskId, "EXPORT_INTERRUPTED", "应用重启前的导出任务已中断", Instant.now()));
        repository.findPendingIds(capacity).forEach(dispatcher::submit);
    }
}
