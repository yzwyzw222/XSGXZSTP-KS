package com.aacv.system.graph.application;

import com.aacv.system.graph.application.port.GraphProjectionRequestPort;
import com.aacv.system.graph.domain.GraphAchievementSnapshot;
import com.aacv.system.graph.infrastructure.neo4j.Neo4jProjectionInspector;
import com.aacv.system.graph.infrastructure.persistence.GraphMaintenanceMapper;
import com.aacv.system.graph.infrastructure.persistence.MyBatisGraphSnapshotReader;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class GraphMaintenancePageService {

    public static final int PAGE_SIZE = 100;
    private final GraphMaintenanceMapper mapper;
    private final GraphProjectionRequestPort requestPort;
    private final MyBatisGraphSnapshotReader snapshotReader;
    private final Neo4jProjectionInspector inspector;

    public GraphMaintenancePageService(
            GraphMaintenanceMapper mapper,
            GraphProjectionRequestPort requestPort,
            MyBatisGraphSnapshotReader snapshotReader,
            Neo4jProjectionInspector inspector) {
        this.mapper = mapper;
        this.requestPort = requestPort;
        this.snapshotReader = snapshotReader;
        this.inspector = inspector;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processNext(long runId, long cursor, boolean forceRefresh) {
        List<Long> ids = mapper.findAchievementPage(cursor, PAGE_SIZE);
        if (ids.isEmpty()) {
            return 0;
        }
        long repaired = 0;
        long differences = 0;
        for (Long achievementId : ids) {
            boolean refresh = forceRefresh;
            if (!forceRefresh) {
                Long desiredVersion = mapper.findDesiredVersion(achievementId);
                GraphAchievementSnapshot snapshot = snapshotReader.load(achievementId);
                refresh = desiredVersion == null || !inspector.matches(snapshot, desiredVersion);
            }
            if (refresh) {
                requestPort.requestAchievement(achievementId);
                repaired++;
                differences++;
            }
        }
        long lastId = ids.getLast();
        if (mapper.advanceProgress(runId, lastId, ids.size(), repaired, differences) != 1) {
            throw new IllegalStateException("图维护进度更新数量异常");
        }
        return ids.size();
    }
}
