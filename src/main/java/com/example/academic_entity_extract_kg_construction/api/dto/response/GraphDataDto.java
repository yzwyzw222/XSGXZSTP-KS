package com.example.academic_entity_extract_kg_construction.api.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GraphDataDto {

    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    @Data
    @Builder
    public static class GraphNode {
        private NodeData data;
    }

    @Data
    @Builder
    public static class GraphEdge {
        private EdgeData data;
    }

    @Data
    @Builder
    public static class NodeData {
        private String id;
        private String label;
        private String type;
        private Map<String, Object> properties;
    }

    @Data
    @Builder
    public static class EdgeData {
        private String id;
        private String source;
        private String target;
        private String label;
        private String type;
    }
}
