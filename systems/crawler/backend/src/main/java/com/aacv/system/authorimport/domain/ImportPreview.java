package com.aacv.system.authorimport.domain;

import java.util.List;
import java.util.Map;

public record ImportPreview(
        String previewKey, List<String> sheets, List<String> headers, Map<String, Integer> mapping,
        int totalRows, int validRows, List<ImportRow> rows, List<RowIssue> issues) {
    public record RowIssue(int rowNumber, List<String> errors, List<String> warnings) { }
}
