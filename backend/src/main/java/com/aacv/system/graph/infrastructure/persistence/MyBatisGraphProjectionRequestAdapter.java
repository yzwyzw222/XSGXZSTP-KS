package com.aacv.system.graph.infrastructure.persistence;

import com.aacv.system.graph.application.port.GraphProjectionRequestPort;
import com.aacv.system.graph.domain.GraphNodeType;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MyBatisGraphProjectionRequestAdapter implements GraphProjectionRequestPort {

    private final GraphProjectionRequestMapper mapper;
    private final Clock clock;

    public MyBatisGraphProjectionRequestAdapter(GraphProjectionRequestMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String requestAchievement(long achievementId) {
        if (achievementId < 1) {
            throw new IllegalArgumentException("成果ID必须为正数");
        }
        Instant now = clock.instant();
        int affectedRows = mapper.advanceDesiredVersion(achievementId, now);
        if (affectedRows < 1 || affectedRows > 2) {
            throw new IllegalStateException("图投影期望版本更新数量异常");
        }
        long desiredVersion = mapper.findDesiredVersion(achievementId);
        String eventId = UUID.randomUUID().toString();
        if (mapper.insertOutboxEvent(eventId, achievementId, desiredVersion, now) != 1) {
            throw new IllegalStateException("图投影Outbox事件写入数量异常");
        }
        return eventId;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requestRelated(GraphNodeType entityType, Collection<Long> entityIds) {
        if (entityType == null || entityIds == null || entityIds.isEmpty()) {
            throw new IllegalArgumentException("图投影关联实体不能为空");
        }
        LinkedHashSet<Long> checkedIds = new LinkedHashSet<>();
        for (Long entityId : entityIds) {
            if (entityId == null || entityId < 1) {
                throw new IllegalArgumentException("关联实体ID必须为正数");
            }
            checkedIds.add(entityId);
        }
        for (Long achievementId : mapper.findRelatedAchievementIds(entityType.name(), checkedIds)) {
            requestAchievement(achievementId);
        }
    }
}
