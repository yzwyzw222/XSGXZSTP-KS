package com.aacv.system.graph.api;

import com.aacv.system.graph.application.GraphQueryService;
import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.graph.domain.GraphRelationshipType;
import com.aacv.system.graph.domain.GraphView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/graph")
public class GraphController {

    private final GraphQueryService queryService;

    public GraphController(GraphQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/subgraph")
    public GraphView subgraph(
            @RequestParam GraphNodeType centerType,
            @RequestParam @Min(1) long centerId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(2) int depth,
            @RequestParam(defaultValue = "100") @Min(1) @Max(300) int nodeLimit,
            @RequestParam(required = false) List<GraphRelationshipType> relationshipTypes,
            @RequestParam(required = false) List<GraphNodeType> nodeTypes,
            @RequestParam(required = false) @Min(1000) @Max(9999) Integer publicationYearFrom,
            @RequestParam(required = false) @Min(1000) @Max(9999) Integer publicationYearTo,
            @RequestParam(required = false) List<String> achievementTypes) {
        return queryService.subgraph(
                centerType, centerId, depth, nodeLimit, relationshipTypes, nodeTypes,
                publicationYearFrom, publicationYearTo, achievementTypes);
    }

    @GetMapping("/path")
    public GraphView path(
            @RequestParam GraphNodeType sourceType,
            @RequestParam @Min(1) long sourceId,
            @RequestParam GraphNodeType targetType,
            @RequestParam @Min(1) long targetId,
            @RequestParam(defaultValue = "6") @Min(1) @Max(6) int maxHops) {
        return queryService.path(sourceType, sourceId, targetType, targetId, maxHops);
    }
}
