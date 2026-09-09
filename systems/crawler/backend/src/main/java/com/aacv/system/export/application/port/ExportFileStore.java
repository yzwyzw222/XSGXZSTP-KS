package com.aacv.system.export.application.port;

import com.aacv.system.export.domain.ExportFormat;
import com.aacv.system.export.domain.ExportRecord;
import java.nio.file.Path;
import java.util.List;

public interface ExportFileStore {
    StoredExport write(String taskId, ExportFormat format, List<ExportRecord> records);

    Path resolve(String relativePath);

    void delete(String relativePath);

    record StoredExport(String fileName, String relativePath) {
    }
}
