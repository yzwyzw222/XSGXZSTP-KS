package com.aacv.system.export.infrastructure.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.export.domain.ExportFormat;
import com.aacv.system.export.domain.ExportRecord;
import com.aacv.system.export.infrastructure.config.ExportProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class LocalExportFileStoreTests {

    @TempDir
    java.nio.file.Path directory;

    @Test
    void writesUtf8CsvAndNeutralizesSpreadsheetFormulas() throws Exception {
        LocalExportFileStore store = store();
        ExportRecord record = new ExportRecord(
                1, "=2+2", "10.1/\"quoted\"", "article", "zh", LocalDate.of(2026, 9, 2), "测试期刊");

        var stored = store.write("00000000-0000-0000-0000-000000000001", ExportFormat.CSV, List.of(record));
        String content = Files.readString(store.resolve(stored.relativePath()), StandardCharsets.UTF_8);

        assertTrue(content.startsWith("\ufeffid,title,doi"));
        assertTrue(content.contains("\"'=2+2\""));
        assertTrue(content.contains("\"10.1/\"\"quoted\"\"\""));
        assertFalse(Files.exists(directory.resolve(stored.fileName() + ".tmp")));
    }

    @Test
    void writesJsonAndRejectsPathsOutsideConfiguredRoot() throws Exception {
        LocalExportFileStore store = store();
        ExportRecord record = new ExportRecord(2, "安全导出", null, "dataset", null, null, null);

        var stored = store.write("00000000-0000-0000-0000-000000000002", ExportFormat.JSON, List.of(record));
        String content = Files.readString(store.resolve(stored.relativePath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("\"title\":\"安全导出\""));
        assertEquals("aacv-achievements-00000000-0000-0000-0000-000000000002.json", stored.fileName());
        assertThrows(IllegalArgumentException.class, () -> store.resolve("../outside.json"));
    }

    private LocalExportFileStore store() {
        ExportProperties properties = new ExportProperties();
        properties.setRootDirectory(directory.toString());
        return new LocalExportFileStore(properties, new ObjectMapper());
    }
}
