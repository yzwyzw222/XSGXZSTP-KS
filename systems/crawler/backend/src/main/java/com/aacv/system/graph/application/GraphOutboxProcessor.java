package com.aacv.system.graph.application;

import com.aacv.system.graph.application.port.GraphProjectionWriter;
import com.aacv.system.graph.domain.GraphOutboxEvent;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class GraphOutboxProcessor {

    private static final Duration LEASE = Duration.ofMinutes(2);

    private final GraphOutboxService outboxService;
    private final ObjectProvider<GraphProjectionWriter> writerProvider;

    public GraphOutboxProcessor(
            GraphOutboxService outboxService, ObjectProvider<GraphProjectionWriter> writerProvider) {
        this.outboxService = outboxService;
        this.writerProvider = writerProvider;
    }

    public int processBatch() {
        String workerId = "graph-" + UUID.randomUUID();
        int processed = 0;
        for (GraphOutboxEvent event : outboxService.claim(workerId, 50, LEASE)) {
            try {
                GraphProjectionWriter writer = writerProvider.getIfAvailable();
                if (writer == null) {
                    throw new IllegalStateException("图投影写入器不可用");
                }
                writer.projectAchievement(event.achievementId(), event.desiredVersion());
                outboxService.succeed(event, workerId);
            } catch (RuntimeException exception) {
                outboxService.fail(event, workerId, "GRAPH_PROJECTION_FAILED");
            }
            processed++;
        }
        return processed;
    }
}
