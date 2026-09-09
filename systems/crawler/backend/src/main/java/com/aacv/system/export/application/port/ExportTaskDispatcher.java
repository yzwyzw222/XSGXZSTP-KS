package com.aacv.system.export.application.port;

public interface ExportTaskDispatcher {
    void submit(String taskId);
}
