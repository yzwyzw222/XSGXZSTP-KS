package com.aacv.api.graph;

import com.aacv.application.graph.ScholarlyGraphService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scholar-graph")
public class ScholarGraphController {

    private final ScholarlyGraphService graphService;

    public ScholarGraphController(ScholarlyGraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/initial")
    public GraphResponse initial() {
        return graphService.loadInitial();
    }

    @GetMapping("/stats")
    public GraphStatsResponse stats() {
        return graphService.loadStats();
    }

    @GetMapping("/expand")
    public GraphResponse expand(@RequestParam String nodeId) {
        return graphService.expand(nodeId);
    }
}