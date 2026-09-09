package com.aacv.system.analytics.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.analytics.application.port.AnalyticsRepository;
import com.aacv.system.analytics.domain.AnalyticsOverview;
import com.aacv.system.analytics.domain.AnalyticsQuery;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTests {

    @Test
    void returnsRepositoryDataWithMatchingScopeAndUpdateTime() {
        AnalyticsRepository repository = mock(AnalyticsRepository.class);
        AnalyticsService service = new AnalyticsService(repository);
        AnalyticsQuery query = new AnalyticsQuery(2025, 2026, null, null, null, null);
        AnalyticsOverview overview = new AnalyticsOverview(2, 3, 2, 1);
        Instant updatedAt = Instant.parse("2026-09-02T10:00:00Z");
        when(repository.overview(query)).thenReturn(overview);
        when(repository.lastUpdatedAt(query)).thenReturn(updatedAt);

        var result = service.overview(query);

        assertEquals(overview, result.value());
        assertEquals(query, result.filters());
        assertEquals(updatedAt, result.updatedAt());
        verify(repository).overview(query);
        verify(repository).lastUpdatedAt(query);
    }

    @Test
    void rejectsUnboundedCollaborationLimitBeforeQuerying() {
        AnalyticsService service = new AnalyticsService(mock(AnalyticsRepository.class));
        AnalyticsQuery query = new AnalyticsQuery(null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> service.collaboration(query, 101));
    }
}
