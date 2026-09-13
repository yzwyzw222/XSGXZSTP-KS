package com.aacv.system.ingestion.infrastructure.persistence;

import com.aacv.system.ingestion.application.port.IngestionRepository;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisIngestionRepository implements IngestionRepository {

    private final IngestionMapper mapper;

    public MyBatisIngestionRepository(IngestionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int clearExpiredPayloads(Instant expiredBefore, int batchSize) {
        if (expiredBefore == null || batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("原始Payload清理参数无效");
        }
        return mapper.clearExpiredPayloads(expiredBefore, batchSize);
    }
}
