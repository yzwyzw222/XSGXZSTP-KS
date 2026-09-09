package com.aacv.system.graph.infrastructure.neo4j;

import com.aacv.system.graph.application.port.GraphProjectionWriter;
import com.aacv.system.graph.domain.GraphAchievementSnapshot;
import com.aacv.system.graph.infrastructure.persistence.MyBatisGraphSnapshotReader;
import org.springframework.stereotype.Component;

@Component
public class Neo4jGraphProjectionWriter implements GraphProjectionWriter {

    private final MyBatisGraphSnapshotReader snapshotReader;
    private final Neo4jProjectionTransaction transaction;
    private final GraphSchemaState schemaState;

    public Neo4jGraphProjectionWriter(
            MyBatisGraphSnapshotReader snapshotReader,
            Neo4jProjectionTransaction transaction,
            GraphSchemaState schemaState) {
        this.snapshotReader = snapshotReader;
        this.transaction = transaction;
        this.schemaState = schemaState;
    }

    @Override
    public void projectAchievement(long achievementId, long projectionVersion) {
        if (!schemaState.isReady()) {
            throw new IllegalStateException("Neo4j图约束未就绪");
        }
        GraphAchievementSnapshot snapshot = snapshotReader.load(achievementId);
        transaction.replace(snapshot, projectionVersion);
    }
}
