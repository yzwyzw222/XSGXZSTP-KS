package com.aacv.system.source.infrastructure.crossref;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.source.domain.SourceType;
import com.aacv.system.source.domain.SourceWork;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CrossrefResponseParserTests {

    private CrossrefResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new CrossrefResponseParser(
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-09-02T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void parsesCursorTotalAndRawRecordsFromFixture() throws IOException {
        CrossrefResponseParser.ParsedPage page = parser.parsePage(
                fixture(), Map.of("X-Concurrency-Limit", "1"));

        assertEquals(2, page.records().size());
        assertEquals(SourceType.CROSSREF, page.records().getFirst().sourceType());
        assertEquals("10.1000/abc-1", page.records().getFirst().externalRecordId());
        assertEquals("DnF1ZXJ5VGhlbkZldGNoBQAAAAA1", page.nextCursor().value());
        assertEquals("2", page.responseMetadata().get("message.total-results"));
        assertEquals("1", page.responseMetadata().get("X-Concurrency-Limit"));
    }

    @Test
    void parsesIdentifiersDatePrecisionAndKeepsAbstractOnlyInRawPayload() throws IOException {
        CrossrefResponseParser.ParsedPage page = parser.parsePage(fixture(), Map.of());

        SourceWork work = parser.parseWork(page.records().getFirst());

        assertEquals("10.1000/abc-1", work.doi());
        assertEquals(LocalDate.of(2026, 8, 1), work.publicationDate());
        assertEquals(SourceWork.SourceDatePrecision.MONTH, work.publicationDatePrecision());
        assertEquals("2049-3630", work.primaryVenue().issnL());
        assertEquals(2, work.primaryVenue().issns().size());
        assertEquals("https://orcid.org/0000-0003-1613-5981", work.authorships().getFirst().authorExternalId());
        assertEquals("10.1000/abc-1#author:2", work.authorships().get(1).authorExternalId());
        assertEquals("https://ror.org/03yrm5c26",
                work.authorships().getFirst().organizations().getFirst().rorId());
        assertNull(work.authorships().getFirst().organizations().getFirst().externalId());
        assertNull(work.authorships().get(1).organizations().getFirst().externalId());
        assertEquals("10.2000/ref-1", work.referencedWorkIds().getFirst());
        assertNull(work.abstractText());
        assertTrue(page.records().getFirst().payload().contains("Raw abstract text."));
        assertEquals(Instant.parse("2026-08-31T12:30:00Z"), work.indexedAt());
    }

    @Test
    void emptyAndShortPagesWithoutNextCursorAreTerminal() {
        CrossrefResponseParser.ParsedPage empty = parser.parsePage(
                "{\"message\":{\"total-results\":0,\"items\":[]}}".getBytes(), Map.of());
        CrossrefResponseParser.ParsedPage shortPage = parser.parsePage(
                "{\"message\":{\"total-results\":1,\"items\":[{\"DOI\":\"10.1000/short\"}]}}"
                        .getBytes(),
                Map.of());

        assertTrue(empty.records().isEmpty());
        assertNull(empty.nextCursor());
        assertEquals(1, shortPage.records().size());
        assertNull(shortPage.nextCursor());
    }

    @Test
    void missingOptionalArraysAndDatePrecisionDoNotInventValues() {
        SourceWork missing = parser.parseWork(record("""
                {"DOI":"10.1000/missing","published":{"date-parts":[[2026]]}}
                """));
        SourceWork day = parser.parseWork(record("""
                {"DOI":"10.1000/day","issued":{"date-parts":[[2026,8,2]]},
                 "author":[{"given":"No","family":"Orcid","affiliation":[{"name":"No ROR"}]}]}
                """));

        assertEquals(SourceWork.SourceDatePrecision.YEAR, missing.publicationDatePrecision());
        assertTrue(missing.authorships().isEmpty());
        assertTrue(missing.topics().isEmpty());
        assertTrue(missing.referencedWorkIds().isEmpty());
        assertEquals(SourceWork.SourceDatePrecision.DAY, day.publicationDatePrecision());
        assertNull(day.authorships().getFirst().orcid());
        assertNull(day.authorships().getFirst().organizations().getFirst().rorId());
    }

    @Test
    void rejectsInvalidJsonAndMissingDoi() {
        assertThrows(CrossrefClientException.class, () -> parser.parsePage("not-json".getBytes(), Map.of()));
        CrossrefClientException exception = assertThrows(
                CrossrefClientException.class,
                () -> parser.parsePage(
                        "{\"message\":{\"items\":[{}]}}".getBytes(), Map.of()));
        assertEquals("PARSE", exception.category());
        assertTrue(exception.getMessage().contains("DOI"));
    }

    private byte[] fixture() throws IOException {
        try (var stream = getClass().getResourceAsStream("/crossref/work-page-sample.json")) {
            return java.util.Objects.requireNonNull(stream).readAllBytes();
        }
    }

    private com.aacv.system.ingestion.domain.RawSourceRecord record(String payload) {
        String doi = new ObjectMapper().readTree(payload).get("DOI").asString();
        return new com.aacv.system.ingestion.domain.RawSourceRecord(
                SourceType.CROSSREF,
                doi,
                URI.create("https://api.crossref.org/works/" + doi),
                payload,
                Instant.parse("2026-09-02T01:00:00Z"));
    }
}
