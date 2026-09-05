package com.aacv.system.source.infrastructure.openalex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.source.domain.SourceType;
import com.aacv.system.source.domain.SourceWork;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenAlexResponseParserTests {

    private static final Instant NOW = Instant.parse("2026-09-02T01:00:00Z");
    private final OpenAlexResponseParser parser = new OpenAlexResponseParser(
            new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void fixedOfficialSamplePreservesCursorAndParsesRequiredFields() throws IOException {
        byte[] fixture = fixture();

        OpenAlexResponseParser.ParsedPage page = parser.parsePage(
                fixture, Map.of("X-RateLimit-Remaining", "997"));
        SourceWork work = parser.parseWork(page.records().getFirst());

        assertEquals(1, page.records().size());
        assertEquals(
                "IlsxNjA5MzcyODAwMDAwLCdodHRwczovL29wZW5hbGV4Lm9yZy9XMjc0MTgwOTgwNydd",
                page.nextCursor().value());
        assertEquals("997", page.responseMetadata().get("X-RateLimit-Remaining"));
        assertEquals("https://openalex.org/W2741809807", work.externalId());
        assertEquals("https://doi.org/10.7717/peerj.4375", work.doi());
        assertEquals("PeerJ", work.primaryVenue().displayName());
        assertEquals(2, work.authorships().size());
        assertEquals("OpenAlex", work.authorships().getFirst().organizations().getFirst().displayName());
        assertEquals(1, work.topics().size());
        assertEquals(2, work.referencedWorkIds().size());
        assertEquals("Despite growing interest in Open Access", work.abstractText());
        assertFalse(work.authorshipsMayBeIncomplete());
        assertTrue(work.fieldWarnings().isEmpty());
    }

    @Test
    void malformedAbstractPositionIsObservableAndNeverGuessed() {
        RawSourceRecord record = raw("""
                {
                  "id":"https://openalex.org/W1",
                  "authorships":[],
                  "topics":[],
                  "referenced_works":[],
                  "abstract_inverted_index":{"first":[0],"conflict":[0]}
                }
                """);

        SourceWork work = parser.parseWork(record);

        assertNull(work.abstractText());
        assertTrue(work.fieldWarnings().stream().anyMatch(value -> value.contains("冲突位置")));
    }

    @Test
    void scholarlySignalsSurviveAdapterParsingAndNormalization() {
        SourceWork work = parser.parseWork(raw("""
                {"id":"https://openalex.org/W1","cited_by_count":15,"is_retracted":true,
                 "open_access":{"is_oa":false,"oa_status":"closed"}}
                """));
        var normalized = new com.aacv.system.ingestion.application.SourceWorkNormalizer().normalize(work);
        assertEquals(15L, normalized.scholarlyMetadata().citedByCount());
        assertEquals(true, normalized.scholarlyMetadata().retracted());
        assertEquals(false, normalized.scholarlyMetadata().openAccess());
    }

    @Test
    void oneHundredAuthorshipsAreMarkedAsPotentiallyIncomplete() {
        String authorships = IntStream.rangeClosed(1, 100)
                .mapToObj(index -> "{\"author\":{\"id\":\"https://openalex.org/A" + index
                        + "\",\"display_name\":\"Author " + index + "\"},\"institutions\":[]}")
                .collect(java.util.stream.Collectors.joining(","));
        RawSourceRecord record = raw("""
                {"id":"https://openalex.org/W1","authorships":[%s],"topics":[],"referenced_works":[]}
                """.formatted(authorships));

        SourceWork work = parser.parseWork(record);

        assertEquals(100, work.authorships().size());
        assertTrue(work.authorshipsMayBeIncomplete());
        assertTrue(work.fieldWarnings().stream().anyMatch(value -> value.contains("可能不完整")));
    }

    @Test
    void emptyPageTerminatesAndMissingOptionalFieldsRemainNull() {
        OpenAlexResponseParser.ParsedPage empty = parser.parsePage(
                "{\"meta\":{\"next_cursor\":null},\"results\":[]}".getBytes(StandardCharsets.UTF_8),
                Map.of());
        assertTrue(empty.records().isEmpty());
        assertNull(empty.nextCursor());

        SourceWork work = parser.parseWork(raw("""
                {"id":"https://openalex.org/W1","authorships":[],"topics":[],"referenced_works":[]}
                """));
        assertNull(work.doi());
        assertNull(work.abstractText());
        assertNull(work.primaryVenue());
    }

    @Test
    void invalidJsonAndBlankOpaqueCursorAreRejected() {
        assertThrows(
                OpenAlexClientException.class,
                () -> parser.parsePage("not-json".getBytes(StandardCharsets.UTF_8), Map.of()));
        assertThrows(
                OpenAlexClientException.class,
                () -> parser.parsePage(
                        "{\"meta\":{\"next_cursor\":\" \"},\"results\":[]}".getBytes(StandardCharsets.UTF_8),
                        Map.of()));
    }

    private RawSourceRecord raw(String payload) {
        return new RawSourceRecord(
                SourceType.OPENALEX,
                "https://openalex.org/W1",
                URI.create("https://openalex.org/W1"),
                payload,
                NOW);
    }

    private byte[] fixture() throws IOException {
        try (var stream = getClass().getResourceAsStream("/openalex/work-page-sample.json")) {
            return java.util.Objects.requireNonNull(stream).readAllBytes();
        }
    }
}
