package com.aacv.system.ingestion.application;

import com.aacv.system.ingestion.application.port.IngestionRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RawPayloadRetentionService {

    private final IngestionRepository repository;
    private final Clock clock;

    public RawPayloadRetentionService(IngestionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public CleanupResult cleanupExpired(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("单批清理条数必须在1至1000之间");
        }
        Instant executedAt = clock.instant();
        int cleared = repository.clearExpiredPayloads(executedAt, batchSize);
        return new CleanupResult(batchSize, cleared, executedAt);
    }

    public record CleanupResult(int batchSize, int clearedCount, Instant executedAt) {
    }
}
