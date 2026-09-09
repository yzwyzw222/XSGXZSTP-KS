package com.aacv.system.export.infrastructure.async;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aacv.system.export.application.ExportTaskProcessor;
import com.aacv.system.export.application.ExportTaskFinalizer;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class BoundedExportTaskDispatcherTests {

    @Test
    void marksTaskFailedWhenBoundedExecutorRejectsSubmission() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ExportTaskProcessor processor = mock(ExportTaskProcessor.class);
        ExportTaskFinalizer finalizer = mock(ExportTaskFinalizer.class);
        doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));

        new BoundedExportTaskDispatcher(executor, processor, finalizer).submit("task-1");

        verify(finalizer).fail(
                org.mockito.ArgumentMatchers.eq("task-1"),
                org.mockito.ArgumentMatchers.eq("EXPORT_CONCURRENCY_LIMIT"),
                org.mockito.ArgumentMatchers.eq("导出执行队列已满"),
                any());
    }
}
