package com.aacv.system.graph.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GraphView(
        List<Node> nodes,
        List<Edge> edges,
        String rootNodeId,
        boolean truncated,
        String narrowingSuggestion,
        AppliedLimits appliedLimits,
        Instant syncedAt,
        Long projectionLagSeconds,
        String traceId) {

    public record Node(
            String id, String businessId, GraphNodeType type, String label, Map<String, Object> properties) {
    }

    public record Edge(
            String id,
            GraphRelationshipType type,
            String source,
            String target,
            Map<String, Object> properties) {
    }

    public record AppliedLimits(int depth, int nodeLimit, int maxHops) {
    }
}
