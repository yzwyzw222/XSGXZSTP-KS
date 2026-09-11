package com.aacv.system.authorimport.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ImportRow(
        int rowNumber, String title, String type, List<String> authors,
        List<String> organizations, List<String> keywords, String abstractText,
        LocalDate publicationDate, String datePrecision, String doi, String venue,
        String issn, String url, Map<String, String> original,
        List<String> errors, List<String> warnings) {
}
