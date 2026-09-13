package com.aacv.system.ingestion.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aacv.system.ingestion.application.port.IngestionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RawPayloadRetentionServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-13T00:00:00Z");
    private final IngestionRepository repository = mock(IngestionRepository.class);
    private final RawPayloadRetentionService service =
            new RawPayloadRetentionService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void keepsBoundedCleanupAndReportsTheActualResult() {
        when(repository.clearExpiredPayloads(NOW, 500)).thenReturn(2);
        var result = service.cleanupExpired(500);
        assertEquals(2, result.clearedCount());
        assertEquals(500, result.batchSize());
        assertEquals(NOW, result.executedAt());
        verify(repository).clearExpiredPayloads(NOW, 500);
    }

    @Test
    void rejectsInvalidBatchSizesBeforeAccessingStorage() {
        for (int size : new int[] {-1, 0, 1001}) {
            assertThrows(IllegalArgumentException.class, () -> service.cleanupExpired(size));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void preservesEmptyResultsAndPropagatesStorageFailures() {
        assertEquals(0, service.cleanupExpired(1).clearedCount());
        when(repository.clearExpiredPayloads(NOW, 1000)).thenThrow(new IllegalStateException("存储不可用"));
        assertThrows(IllegalStateException.class, () -> service.cleanupExpired(1000));
    }
}
