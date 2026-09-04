package com.aacv.system.ingestion.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aacv.system.ingestion.domain.NormalizedWork;
import com.aacv.system.source.domain.SourceWork;
import com.aacv.system.source.domain.SourceWork.SourceAuthorship;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAlexWorkNormalizerTests {

    private final OpenAlexWorkNormalizer normalizer = new OpenAlexWorkNormalizer();

    @Test
    void normalizesUnicodeWhitespaceDoiOrcidAndDatePrecision() {
        SourceWork source = new SourceWork(
                "https://openalex.org/W1",
                " HTTPS://DOI.ORG/10.1000/ABC-1 ",
                "  ＯｐｅｎＡｌｅｘ\n  research  ",
                "Article",
                "EN",
                LocalDate.of(2026, 9, 1),
                null,
                List.of(new SourceAuthorship(
                        1,
                        "https://openalex.org/A1",
                        "  Test   Author ",
                        "https://orcid.org/0000-0003-1613-5981",
                        List.of())),
                List.of(),
                List.of(),
                null,
                false,
                List.of());

        NormalizedWork normalized = normalizer.normalize(source);

        assertEquals("OpenAlex research", normalized.titleNormalized());
        assertEquals("10.1000/abc-1", normalized.doi());
        assertEquals("article", normalized.achievementType());
        assertEquals("en", normalized.language());
        assertEquals(NormalizedWork.DatePrecision.DAY, normalized.datePrecision());
        assertEquals("0000-0003-1613-5981", normalized.authorships().getFirst().orcid());
    }

    @Test
    void invalidIdentifiersAndUnknownDateRemainEmptyInsteadOfInventingValues() {
        SourceWork source = new SourceWork(
                "https://openalex.org/W2",
                "not-a-doi",
                null,
                null,
                null,
                null,
                null,
                List.of(new SourceAuthorship(
                        1,
                        "https://openalex.org/A2",
                        null,
                        "0000-0000-0000-0000",
                        List.of())),
                List.of(),
                List.of(),
                null,
                false,
                List.of());

        NormalizedWork normalized = normalizer.normalize(source);

        assertNull(normalized.doi());
        assertNull(normalized.publicationDate());
        assertNull(normalized.datePrecision());
        assertNull(normalized.authorships().getFirst().orcid());
        assertEquals("UNSPECIFIED", normalized.achievementType());
    }

    @Test
    void sameDeterministicIdentityMaterialProducesSameFingerprint() {
        SourceWork first = minimalWork("  Same title ");
        SourceWork second = minimalWork("Same\n title");

        assertEquals(
                normalizer.normalize(first).matchFingerprint(),
                normalizer.normalize(second).matchFingerprint());
    }

    @Test
    void missingSourceAuthorIdentifierStillProducesDeterministicFingerprint() {
        SourceWork source = new SourceWork(
                "10.1000/source-record",
                "10.1000/source-record",
                "Title",
                "journal-article",
                null,
                LocalDate.of(2026, 1, 1),
                null,
                List.of(new SourceAuthorship(1, null, "Same Name", null, List.of())),
                List.of(),
                List.of(),
                null,
                false,
                List.of());

        NormalizedWork first = normalizer.normalize(source);
        NormalizedWork second = normalizer.normalize(source);

        assertEquals(first.matchFingerprint(), second.matchFingerprint());
    }

    private SourceWork minimalWork(String title) {
        return new SourceWork(
                "https://openalex.org/W3",
                null,
                title,
                "article",
                "en",
                LocalDate.of(2026, 1, 1),
                null,
                List.of(new SourceAuthorship(
                        1, "https://openalex.org/A3", "Author", null, List.of())),
                List.of(),
                List.of(),
                null,
                false,
                List.of());
    }
}
