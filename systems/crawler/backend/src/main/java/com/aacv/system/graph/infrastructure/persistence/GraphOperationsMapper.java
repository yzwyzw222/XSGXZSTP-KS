package com.aacv.system.graph.infrastructure.persistence;

import com.aacv.system.graph.domain.GraphEventView;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GraphOperationsMapper {

    long countEvents(String status);

    List<GraphEventView> findEvents(
            @Param("status") String status, @Param("offset") long offset, @Param("size") int size);

    GraphEventView findEvent(String eventId);

    long countByStatus(String status);

    Long oldestPendingAgeSeconds(Instant now);

    Instant lastSucceededAt();

    boolean rebuildInProgress();

    Instant latestProjectedAt();
}
