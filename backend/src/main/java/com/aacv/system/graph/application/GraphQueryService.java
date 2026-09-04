package com.aacv.system.graph.application;

import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.graph.domain.GraphRelationshipType;
import com.aacv.system.graph.domain.GraphView;
import com.aacv.system.graph.domain.GraphView.AppliedLimits;
import com.aacv.system.graph.domain.GraphView.Edge;
import com.aacv.system.graph.domain.GraphView.Node;
import com.aacv.system.graph.infrastructure.neo4j.GraphSchemaState;
import com.aacv.system.shared.application.ResourceNotFoundException;
import com.aacv.system.shared.infrastructure.web.TraceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class GraphQueryService {

    private static final int HARD_NODE_LIMIT = 300;
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(3);
    private static final Set<String> NODE_PROPERTIES = Set.of(
            "title", "achievementType", "language", "publicationDate", "doi",
            "name", "orcid", "standardCode", "countryCode", "venueType", "issn",
            "code", "path", "projectionVersion");

    private final Driver driver;
    private final GraphSchemaState schemaState;
    private final GraphOperationsService operationsService;

    public GraphQueryService(
            Driver driver, GraphSchemaState schemaState, GraphOperationsService operationsService) {
        this.driver = driver;
        this.schemaState = schemaState;
        this.operationsService = operationsService;
    }

    @PreAuthorize("hasAuthority('GRAPH_READ')")
    public GraphView subgraph(
            GraphNodeType centerType,
            long centerId,
            int depth,
            int nodeLimit,
            Collection<GraphRelationshipType> relationshipTypes,
            Collection<GraphNodeType> nodeTypes,
            Integer publicationYearFrom,
            Integer publicationYearTo,
            Collection<String> achievementTypes) {
        validateNode(centerType, centerId);
        if (depth < 1 || depth > 2 || nodeLimit < 1 || nodeLimit > HARD_NODE_LIMIT
                || publicationYearFrom != null && publicationYearTo != null
                && publicationYearFrom > publicationYearTo) {
            throw new IllegalArgumentException("局部子图查询参数无效");
        }
        List<String> relations = names(relationshipTypes, EnumSet.allOf(GraphRelationshipType.class));
        List<String> labels = labels(nodeTypes, EnumSet.allOf(GraphNodeType.class));
        List<String> types = checkedAchievementTypes(achievementTypes);
        String cypher = "MATCH path=(root:" + label(centerType) + " {businessId: $centerId})-[rels*0.."
                + depth + "]-(node) "
                + "WHERE root.aacvManaged = true AND node.aacvManaged = true "
                + "AND all(rel IN relationships(path) WHERE rel.aacvManaged = true AND type(rel) IN $relationshipTypes) "
                + "AND any(nodeLabel IN labels(node) WHERE nodeLabel IN $nodeTypes) "
                + "AND (NOT 'Achievement' IN labels(node) OR $yearFrom IS NULL "
                + "OR node.publicationDate >= date({year: $yearFrom, month: 1, day: 1})) "
                + "AND (NOT 'Achievement' IN labels(node) OR $yearTo IS NULL "
                + "OR node.publicationDate < date({year: $yearTo + 1, month: 1, day: 1})) "
                + "AND (NOT 'Achievement' IN labels(node) OR size($achievementTypes) = 0 "
                + "OR node.achievementType IN $achievementTypes) "
                + "RETURN path ORDER BY length(path), elementId(node) LIMIT $pathLimit";
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("centerId", centerId);
        parameters.put("relationshipTypes", relations);
        parameters.put("nodeTypes", labels);
        parameters.put("yearFrom", publicationYearFrom);
        parameters.put("yearTo", publicationYearTo);
        parameters.put("achievementTypes", types);
        parameters.put("pathLimit", Math.min(1201, nodeLimit * 4 + 1));
        return execute(cypher, parameters, stableNodeId(centerType, centerId), depth, nodeLimit, 0);
    }

    @PreAuthorize("hasAuthority('GRAPH_READ')")
    public GraphView path(
            GraphNodeType sourceType, long sourceId,
            GraphNodeType targetType, long targetId,
            int maxHops) {
        validateNode(sourceType, sourceId);
        validateNode(targetType, targetId);
        if (maxHops < 1 || maxHops > 6) {
            throw new IllegalArgumentException("最短路径跳数无效");
        }
        String cypher = "MATCH path=allShortestPaths((source:" + label(sourceType)
                + " {businessId: $sourceId})-[*1.." + maxHops + "]-(target:" + label(targetType)
                + " {businessId: $targetId})) "
                + "WHERE source.aacvManaged = true AND target.aacvManaged = true "
                + "AND all(rel IN relationships(path) WHERE rel.aacvManaged = true) "
                + "RETURN path ORDER BY reduce(key = '', rel IN relationships(path) | key + '|' + elementId(rel)) LIMIT 1";
        return execute(cypher, Map.of("sourceId", sourceId, "targetId", targetId),
                stableNodeId(sourceType, sourceId), 0, HARD_NODE_LIMIT, maxHops);
    }

    private GraphView execute(
            String cypher, Map<String, Object> parameters, String rootNodeId,
            int depth, int nodeLimit, int maxHops) {
        if (operationsService.rebuildInProgress()) {
            throw new GraphRebuildInProgressException();
        }
        if (!schemaState.isReady()) {
            throw new GraphUnavailableException();
        }
        try (var session = driver.session()) {
            List<Record> records = session.executeRead(
                    transaction -> transaction.run(cypher, parameters).list(),
                    TransactionConfig.builder().withTimeout(QUERY_TIMEOUT).build());
            if (records.isEmpty()) {
                throw new ResourceNotFoundException("图节点或路径不存在");
            }
            LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
            LinkedHashMap<String, Edge> edges = new LinkedHashMap<>();
            boolean truncated = false;
            for (Record record : records) {
                Path path = record.get("path").asPath();
                for (org.neo4j.driver.types.Node graphNode : path.nodes()) {
                    Node node = node(graphNode);
                    if (!nodes.containsKey(node.id()) && nodes.size() >= nodeLimit) {
                        truncated = true;
                        continue;
                    }
                    nodes.putIfAbsent(node.id(), node);
                }
                for (Relationship relationship : path.relationships()) {
                    Node source = nodeByElement(path.nodes(), relationship.startNodeElementId());
                    Node target = nodeByElement(path.nodes(), relationship.endNodeElementId());
                    if (nodes.containsKey(source.id()) && nodes.containsKey(target.id())) {
                        Edge edge = edge(relationship, source.id(), target.id());
                        edges.putIfAbsent(edge.id(), edge);
                    }
                }
            }
            Instant syncedAt = operationsService.latestProjectedAt();
            Long lag = syncedAt == null ? null : Math.max(0, Duration.between(syncedAt, Instant.now()).toSeconds());
            return new GraphView(
                    new ArrayList<>(nodes.values()), new ArrayList<>(edges.values()), rootNodeId,
                    truncated, truncated ? "请缩小节点类型、关系类型或年份范围" : null,
                    new AppliedLimits(depth, nodeLimit, maxHops), syncedAt, lag, TraceContext.current());
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (Neo4jException exception) {
            if (isTimeout(exception)) {
                throw new GraphQueryTimeoutException();
            }
            throw new GraphUnavailableException();
        }
    }

    static boolean isTimeout(Neo4jException exception) {
        String code = exception.code();
        return code != null && code.contains("TransactionTimedOut");
    }

    private Node node(org.neo4j.driver.types.Node graphNode) {
        GraphNodeType type = graphNodeType(graphNode.labels());
        long businessId = graphNode.get("businessId").asLong();
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        for (String key : NODE_PROPERTIES) {
            if (graphNode.containsKey(key) && !graphNode.get(key).isNull()) {
                properties.put(key, graphNode.get(key).asObject());
            }
        }
        String label = graphNode.containsKey("title") && !graphNode.get("title").isNull()
                ? graphNode.get("title").asString()
                : graphNode.containsKey("name") && !graphNode.get("name").isNull()
                ? graphNode.get("name").asString() : type.name() + " " + businessId;
        return new Node(stableNodeId(type, businessId), Long.toString(businessId), type, label, properties);
    }

    private Edge edge(Relationship relationship, String source, String target) {
        GraphRelationshipType type = GraphRelationshipType.valueOf(relationship.type());
        String achievementId = relationship.containsKey("achievementBusinessId")
                ? relationship.get("achievementBusinessId").toString() : "none";
        String id = type.name() + ":" + source + ":" + target + ":" + achievementId;
        return new Edge(id, type, source, target, Map.of());
    }

    private Node nodeByElement(Iterable<org.neo4j.driver.types.Node> nodes, String elementId) {
        for (org.neo4j.driver.types.Node node : nodes) {
            if (node.elementId().equals(elementId)) {
                return node(node);
            }
        }
        throw new IllegalStateException("图关系端点缺失");
    }

    private GraphNodeType graphNodeType(Iterable<String> labels) {
        for (String current : labels) {
            for (GraphNodeType type : GraphNodeType.values()) {
                if (label(type).equals(current)) {
                    return type;
                }
            }
        }
        throw new IllegalStateException("图节点类型不受支持");
    }

    private void validateNode(GraphNodeType type, long id) {
        if (type == null || id < 1) {
            throw new IllegalArgumentException("图节点参数无效");
        }
    }

    private String label(GraphNodeType type) {
        return switch (type) {
            case ACHIEVEMENT -> "Achievement";
            case AUTHOR -> "Author";
            case INSTITUTION -> "Institution";
            case VENUE -> "Venue";
            case TOPIC -> "Topic";
        };
    }

    private String stableNodeId(GraphNodeType type, long businessId) {
        return type.name() + ":" + businessId;
    }

    private <E extends Enum<E>> List<String> names(Collection<E> values, Set<E> defaults) {
        Collection<E> selected = values == null || values.isEmpty() ? defaults : new LinkedHashSet<>(values);
        return selected.stream().map(Enum::name).toList();
    }

    private List<String> labels(Collection<GraphNodeType> values, Set<GraphNodeType> defaults) {
        Collection<GraphNodeType> selected = values == null || values.isEmpty() ? defaults : new LinkedHashSet<>(values);
        return selected.stream().map(this::label).toList();
    }

    private List<String> checkedAchievementTypes(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(String::trim).peek(value -> {
            if (value.isEmpty() || value.length() > 64) {
                throw new IllegalArgumentException("成果类型筛选值无效");
            }
        }).distinct().toList();
    }
}
