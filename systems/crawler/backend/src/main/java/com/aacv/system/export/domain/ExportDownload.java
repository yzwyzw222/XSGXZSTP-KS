package com.aacv.system.export.domain;

import java.nio.file.Path;

public record ExportDownload(Path path, String fileName, String contentType) {
}
