package com.aacv.system.export.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.export.application.port.ExportRepository;
import com.aacv.system.export.application.port.ExportTaskDispatcher;
import com.aacv.system.export.infrastructure.config.ExportProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportRecoveryServiceTests {

    @Test
    void failsInterruptedRunningTasksAndSubmitsOnlyBoundedPendingTasks() {
        ExportRepository repository = mock(ExportRepository.class);
        ExportTaskDispatcher dispatcher = mock(ExportTaskDispatcher.class);
        ExportTaskFinalizer finalizer = mock(ExportTaskFinalizer.class);
        ExportProperties properties = new ExportProperties();
        when(repository.findRunningIds()).thenReturn(List.of("running-task"));
        when(repository.findPendingIds(22)).thenReturn(List.of("task-1", "task-2"));

        new ExportRecoveryService(repository, dispatcher, finalizer, properties).recover();

        verify(finalizer).fail(
                org.mockito.ArgumentMatchers.eq("running-task"),
                org.mockito.ArgumentMatchers.eq("EXPORT_INTERRUPTED"),
                org.mockito.ArgumentMatchers.eq("应用重启前的导出任务已中断"),
                any());
        verify(repository).findPendingIds(22);
        verify(dispatcher).submit("task-1");
        verify(dispatcher).submit("task-2");
    }
}
