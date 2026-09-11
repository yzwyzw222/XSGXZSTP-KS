package com.aacv.system.authorimport.domain;

import java.util.List;
import java.util.Map;

public final class ImportBundle {
    private ImportBundle() { }

    public record FileSettings(int sheetIndex, int headerRow, Map<String, Integer> mapping) {
        public FileSettings {
            mapping = mapping == null ? Map.of() : Map.copyOf(mapping);
            if (sheetIndex < 0 || sheetIndex >= 20 || headerRow < 1 || headerRow > 20 || mapping.size() > 20) {
                throw new IllegalArgumentException("工作表、表头行或字段映射无效");
            }
        }
    }

    public record Options(String scholarName, List<FileSettings> files) {
        public Options {
            scholarName = scholarName == null ? "" : scholarName.strip();
            if (scholarName.length() > 200 || scholarName.codePoints().anyMatch(Character::isISOControl)
                    || files == null || files.isEmpty() || files.size() > 10 || files.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("请选择同一学者的 1 至 10 份文件及有效候选姓名");
            }
            files = List.copyOf(files);
        }
    }

    public record FilePreview(String fileName, List<String> modes, ImportPreview preview) { }

    public record Preview(String previewKey, String scholarName, List<String> candidates,
            List<String> organizations, List<String> messages, boolean canConfirm,
            int totalRows, int validRows, List<FilePreview> files) { }

    public record Summary(long authorId, String scholarName, int importedCount, int linkedCount,
            int skippedCount, List<ImportSummary> batches) { }
}
