package com.aacv.system.source.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.aacv.system.source.domain.ScholarlyMetadata.VersionRelation;
import java.time.Instant;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ScholarlyMetadataParserTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final Instant OBSERVED = Instant.parse("2026-09-05T00:00:00Z");

    @Test
    void preservesZeroFalseAndObservationTimeWithoutTreatingMissingAsFalse() {
        var warnings = new ArrayList<String>();
        var metadata = ScholarlyMetadataParser.openAlex(mapper.readTree("""
                {"cited_by_count":0,"is_retracted":false,"open_access":{"is_oa":true,"oa_status":"diamond"}}
                """), OBSERVED, warnings);
        assertEquals(0L, metadata.citedByCount());
        assertEquals(false, metadata.retracted());
        assertEquals(true, metadata.openAccess());
        assertEquals("diamond", metadata.openAccessStatus());
        assertEquals(OBSERVED, metadata.observedAt());
        assertTrue(warnings.isEmpty());
        var unknown = ScholarlyMetadataParser.openAlex(mapper.readTree("{}"), OBSERVED, warnings);
        assertNull(unknown.citedByCount());
        assertNull(unknown.retracted());
        assertNull(unknown.openAccess());
    }

    @Test
    void invalidMetricsBecomeUnknownWithWarningsRatherThanFabricatedZero() {
        var warnings = new ArrayList<String>();
        for (String count : new String[] {"-1", "1.5", "9223372036854775808", "\"12\""}) {
            var metadata = ScholarlyMetadataParser.openAlex(mapper.readTree("""
                    {"cited_by_count":%s,"is_retracted":"false","open_access":{"is_oa":0,"oa_status":"unrecognised"}}
                    """.formatted(count)), OBSERVED, warnings);
            assertNull(metadata.citedByCount());
            assertNull(metadata.retracted());
            assertNull(metadata.openAccess());
            assertNull(metadata.openAccessStatus());
        }
        assertEquals(16, warnings.size());
    }

    @Test
    void crossrefKeepsExplicitVersionRelationsDistinctFromCitationsAndNotices() {
        var warnings = new ArrayList<String>();
        var metadata = ScholarlyMetadataParser.crossref(mapper.readTree("""
                {"is-referenced-by-count":17,"license":[{"URL":"https://creativecommons.org/licenses/by/4.0/"}],
                 "update-to":[{"type":"retraction","DOI":"10.1000/another-work"}],
                 "relation":{"is-preprint-of":[{"id-type":"doi","id":"https://doi.org/10.1000/PUBLISHED"},
                                               {"id-type":"doi","id":"10.1000/published"},
                                               {"id-type":"doi","id":"javascript:bad"}],
                             "cites":[{"id-type":"doi","id":"10.1000/cited"}]}}
                """), OBSERVED, warnings);
        assertEquals(17L, metadata.citedByCount());
        assertNull(metadata.retracted());
        assertNull(metadata.openAccess());
        assertEquals(java.util.List.of(new VersionRelation("is-preprint-of", "10.1000/published")), metadata.versionRelations());
        assertEquals(1, warnings.size());
    }

    @Test
    void oldNormalizedSnapshotsDeserializeWithUnknownScholarlyMetadata() {
        var old = mapper.readValue("""
                {"externalId":"10.1000/old","achievementType":"article","matchFingerprint":"%s","authorshipsMayBeIncomplete":false}
                """.formatted("a".repeat(64)), com.aacv.system.ingestion.domain.NormalizedWork.class);
        assertNull(old.scholarlyMetadata());
    }
}
