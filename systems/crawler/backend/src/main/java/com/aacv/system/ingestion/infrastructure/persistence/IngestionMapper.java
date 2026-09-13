package com.aacv.system.ingestion.infrastructure.persistence;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
interface IngestionMapper {

    int clearExpiredPayloads(
            @Param("expiredBefore") Instant expiredBefore,
            @Param("batchSize") int batchSize);
}
