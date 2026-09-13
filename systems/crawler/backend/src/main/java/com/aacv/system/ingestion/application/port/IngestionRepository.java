package com.aacv.system.ingestion.application.port;

import java.time.Instant;

/** 仅保留历史原始数据的到期清理，不再提供采集写入能力。 */
public interface IngestionRepository {

    int clearExpiredPayloads(Instant expiredBefore, int batchSize);
}
