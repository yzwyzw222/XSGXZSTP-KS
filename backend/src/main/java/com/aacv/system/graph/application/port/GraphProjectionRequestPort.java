package com.aacv.system.graph.application.port;

import com.aacv.system.graph.domain.GraphNodeType;
import java.util.Collection;

public interface GraphProjectionRequestPort {

    String requestAchievement(long achievementId);

    void requestRelated(GraphNodeType entityType, Collection<Long> entityIds);
}
