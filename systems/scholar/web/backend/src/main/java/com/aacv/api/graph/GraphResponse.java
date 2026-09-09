package com.aacv.api.graph;

import java.util.List;

/**
 * 知识图谱响应：节点 + 边 + 元数据。
 */
public class GraphResponse {

    private final List<GraphNode> nodes;
    private final List<GraphEdge> edges;
    private final long totalNodes;
    private final long totalEdges;

    public GraphResponse(List<GraphNode> nodes, List<GraphEdge> edges, long totalNodes, long totalEdges) {
        this.nodes = nodes;
        this.edges = edges;
        this.totalNodes = totalNodes;
        this.totalEdges = totalEdges;
    }

    public List<GraphNode> getNodes() {
        return nodes;
    }

    public List<GraphEdge> getEdges() {
        return edges;
    }

    public long getTotalNodes() {
        return totalNodes;
    }

    public long getTotalEdges() {
        return totalEdges;
    }

    /**
     * 图谱节点。
     */
    public static class GraphNode {
        private final String id;          // 唯一节点 id（含类型前缀）
        private final String label;       // 展示名称
        private final String type;        // paper|author|institution|topic|venue
        private final int degree;         // 相连边数（用于节点大小）

        public GraphNode(String id, String label, String type, int degree) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.degree = degree;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getType() {
            return type;
        }

        public int getDegree() {
            return degree;
        }
    }

    /**
     * 图谱边。
     */
    public static class GraphEdge {
        private final String id;
        private final String source;       // 节点 id
        private final String target;       // 节点 id
        private final String type;         // authoredBy|publishedAt|institutionOf|hasTopic|cites

        public GraphEdge(String id, String source, String target, String type) {
            this.id = id;
            this.source = source;
            this.target = target;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public String getSource() {
            return source;
        }

        public String getTarget() {
            return target;
        }

        public String getType() {
            return type;
        }
    }
}