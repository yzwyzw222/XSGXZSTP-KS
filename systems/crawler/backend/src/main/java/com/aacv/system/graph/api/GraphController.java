package com.aacv.system.graph.api;

import com.aacv.system.graph.application.GraphQueryService;
import com.aacv.system.graph.application.GraphPresentationService;
import com.aacv.system.graph.domain.GraphNodeType;
import com.aacv.system.graph.domain.GraphRelationshipType;
import com.aacv.system.graph.domain.GraphView;
import com.aacv.system.graph.domain.AuthorGraphView;
import com.aacv.system.graph.domain.AuthorGraphView.WorkCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/graph")
public class GraphController {

    private final GraphQueryService queryService;
    private final GraphPresentationService presentationService;

    public GraphController(GraphQueryService queryService, GraphPresentationService presentationService) {
        this.queryService = queryService;
        this.presentationService = presentationService;
    }

    @GetMapping("/overview")
    public GraphView overview(
            @RequestParam(defaultValue = "300") @Min(1) @Max(300) int nodeLimit) {
        return presentationService.present(queryService.overview(nodeLimit), true);
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
            @RequestParam(required = false) List<String> achievementTypes,
            @RequestParam(defaultValue = "false") boolean includeCoauthors) {
        return presentationService.present(queryService.subgraph(
                centerType, centerId, depth, nodeLimit, relationshipTypes, nodeTypes,
                publicationYearFrom, publicationYearTo, achievementTypes), includeCoauthors);
    }

    @GetMapping("/authors/{authorId}")
    public AuthorGraphView authorGraph(
            @PathVariable @Min(1) long authorId,
            @RequestParam(required = false) WorkCategory category,
            @RequestParam(defaultValue = "false") boolean collaborationsOnly,
            @RequestParam(defaultValue = "false") boolean chronological,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        AuthorGraphView result = queryService.authorGraph(authorId, category, collaborationsOnly, chronological, page, size);
        return new AuthorGraphView(presentationService.presentAuthor(result.graph(), collaborationsOnly),
                result.page(), result.size(), result.totalWorks());
    }

    @GetMapping("/path")
    public GraphView path(
            @RequestParam GraphNodeType sourceType,
            @RequestParam @Min(1) long sourceId,
            @RequestParam GraphNodeType targetType,
            @RequestParam @Min(1) long targetId,
            @RequestParam(defaultValue = "6") @Min(1) @Max(6) int maxHops) {
        return presentationService.present(queryService.path(sourceType, sourceId, targetType, targetId, maxHops), false);
    }
}
