package com.aacv.system.source.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.operations.application.AuditService;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSourceServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-02T01:00:00Z");

    private DataSourceRepository repository;
    private AuditService auditService;
    private DataSourceService service;

    @BeforeEach
    void setUp() {
        repository = mock(DataSourceRepository.class);
        auditService = mock(AuditService.class);
        service = new DataSourceService(
                repository,
                new DataSourceAdapterRegistry(List.of()),
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createUsesOnlyFixedOpenAlexIdentityAndTrimsComplianceNote() {
        SourceConnectionSettings settings = settings();
        when(repository.insert(any())).thenAnswer(invocation -> {
            DataSourceConfiguration candidate = invocation.getArgument(0);
            return new DataSourceConfiguration(
                    1,
                    candidate.sourceCode(),
                    candidate.sourceType(),
                    candidate.baseUri(),
                    candidate.enabled(),
                    candidate.settings(),
                    candidate.complianceNote(),
                    null,
                    null,
                    0,
                    0,
                    candidate.createdAt(),
                    candidate.updatedAt());
        });

        DataSourceConfiguration created = service.create(settings, "  仅用于合规科研采集  ");

        assertEquals(DataSourceConfiguration.OPENALEX_CODE, created.sourceCode());
        assertEquals(SourceType.OPENALEX, created.sourceType());
        assertEquals(DataSourceConfiguration.OPENALEX_BASE_URI, created.baseUri());
        assertEquals("仅用于合规科研采集", created.complianceNote());
        assertEquals(NOW, created.createdAt());
    }

    @Test
    void createCrossrefUsesOnlyFixedOfficialIdentity() {
        when(repository.insert(any())).thenAnswer(invocation -> {
            DataSourceConfiguration candidate = invocation.getArgument(0);
            return new DataSourceConfiguration(
                    2,
                    candidate.sourceCode(),
                    candidate.sourceType(),
                    candidate.baseUri(),
                    candidate.enabled(),
                    candidate.settings(),
                    candidate.complianceNote(),
                    null,
                    null,
                    0,
                    0,
                    candidate.createdAt(),
                    candidate.updatedAt());
        });

        DataSourceConfiguration created = service.create(SourceType.CROSSREF, settings(), "Crossref合规采集");

        assertEquals(DataSourceConfiguration.CROSSREF_CODE, created.sourceCode());
        assertEquals(SourceType.CROSSREF, created.sourceType());
        assertEquals(DataSourceConfiguration.CROSSREF_BASE_URI, created.baseUri());
    }

    @Test
    void duplicateOpenAlexSourceIsRejectedBeforeInsert() {
        when(repository.existsByCode(DataSourceConfiguration.OPENALEX_CODE)).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> service.create(settings(), "合规说明"));

        verify(repository, never()).insert(any());
    }

    @Test
    void optimisticUpdateConflictIsReported() {
        DataSourceConfiguration current = configuration(2, true, 3);
        when(repository.lockById(2)).thenReturn(Optional.of(current));
        when(repository.update(any(), org.mockito.ArgumentMatchers.eq(3L))).thenReturn(Optional.empty());

        assertThrows(ResourceConflictException.class, () -> service.update(2, settings(), "新说明", 3));
    }

    @Test
    void invalidRequestBudgetIsRejectedAtDomainBoundary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceConnectionSettings(
                        11, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 2, 1024 * 1024));
    }

    @Test
    void arbitraryUrlCannotCrossFixedOpenAlexBoundary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DataSourceConfiguration(
                        0,
                        DataSourceConfiguration.OPENALEX_CODE,
                        SourceType.OPENALEX,
                        URI.create("https://example.invalid/redirect"),
                        true,
                        settings(),
                        "合规说明",
                        null,
                        null,
                        0,
                        0,
                        NOW,
                        NOW));
    }

    @Test
    void arbitraryCodeCannotCrossFixedCrossrefBoundary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DataSourceConfiguration(
                        0,
                        DataSourceConfiguration.OPENALEX_CODE,
                        SourceType.CROSSREF,
                        DataSourceConfiguration.CROSSREF_BASE_URI,
                        true,
                        settings(),
                        "合规说明",
                        null,
                        null,
                        0,
                        0,
                        NOW,
                        NOW));
    }

    private SourceConnectionSettings settings() {
        return new SourceConnectionSettings(
                2, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 2, 2 * 1024 * 1024);
    }

    private DataSourceConfiguration configuration(long id, boolean enabled, long version) {
        return new DataSourceConfiguration(
                id,
                DataSourceConfiguration.OPENALEX_CODE,
                SourceType.OPENALEX,
                DataSourceConfiguration.OPENALEX_BASE_URI,
                enabled,
                settings(),
                "合规说明",
                null,
                null,
                0,
                version,
                NOW,
                NOW);
    }
}
