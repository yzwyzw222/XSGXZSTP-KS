package com.aacv.system.export.infrastructure.file;

import com.aacv.system.export.application.port.ExportFileStore;
import com.aacv.system.export.domain.ExportFormat;
import com.aacv.system.export.domain.ExportRecord;
import com.aacv.system.export.infrastructure.config.ExportProperties;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class LocalExportFileStore implements ExportFileStore {

    private static final List<String> CSV_HEADERS = List.of(
            "id", "title", "doi", "achievementType", "language", "publicationDate", "primaryVenue");

    private final Path root;
    private final ObjectMapper objectMapper;

    LocalExportFileStore(ExportProperties properties, ObjectMapper objectMapper) {
        properties.validate();
        this.root = Path.of(properties.getRootDirectory()).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("导出根目录不可用", exception);
        }
    }

    @Override
    public StoredExport write(String taskId, ExportFormat format, List<ExportRecord> records) {
        String extension = format == ExportFormat.CSV ? "csv" : "json";
        String fileName = "aacv-achievements-" + taskId + "." + extension;
        Path target = safeResolve(fileName);
        Path temporary = safeResolve(fileName + ".tmp");
        try {
            Files.deleteIfExists(temporary);
            if (format == ExportFormat.CSV) {
                writeCsv(temporary, records);
            } else {
                writeJson(temporary, records);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return new StoredExport(fileName, fileName);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                exception.addSuppressed(ignored);
            }
            throw new IllegalStateException("导出文件生成失败", exception);
        }
    }

    @Override
    public Path resolve(String relativePath) {
        Path path = safeResolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("导出文件不可用");
        }
        return path;
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null) return;
        try {
            Files.deleteIfExists(safeResolve(relativePath));
        } catch (IOException exception) {
            throw new IllegalStateException("过期导出文件清理失败", exception);
        }
    }

    private void writeJson(Path path, List<ExportRecord> records) throws IOException {
        try (OutputStream output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            objectMapper.writeValue(output, records);
        }
    }

    private void writeCsv(Path path, List<ExportRecord> records) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            writer.write('\ufeff');
            writer.write(String.join(",", CSV_HEADERS));
            writer.newLine();
            for (ExportRecord record : records) {
                writer.write(String.join(",", List.of(
                        csv(Long.toString(record.id())),
                        csv(record.title()),
                        csv(record.doi()),
                        csv(record.achievementType()),
                        csv(record.language()),
                        csv(record.publicationDate() == null ? null : record.publicationDate().toString()),
                        csv(record.primaryVenue()))));
                writer.newLine();
            }
        }
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        String leadingTrimmed = safe.stripLeading();
        if (!leadingTrimmed.isEmpty() && "=+-@\t\r".indexOf(leadingTrimmed.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private Path safeResolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("导出文件标识无效");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("导出文件路径越界");
        }
        return resolved;
    }
}
