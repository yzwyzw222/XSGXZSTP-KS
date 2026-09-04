package com.aacv.system.graph.api;

import com.aacv.system.graph.application.GraphOperationsService;
import com.aacv.system.graph.domain.GraphEventView;
import com.aacv.system.graph.domain.GraphOutboxStatus;
import com.aacv.system.graph.domain.GraphSyncStatus;
import com.aacv.system.shared.domain.PageResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class GraphOperationsController {

    private final GraphOperationsService operationsService;

    public GraphOperationsController(GraphOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping("/graph/sync-status")
    public GraphSyncStatus status() {
        return operationsService.status();
    }

    @GetMapping("/operations/graph-events")
    public PageResult<GraphEventView> events(
            @RequestParam(required = false) GraphOutboxStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return operationsService.events(status, page, size);
    }

    @PostMapping("/operations/graph-events/{eventId}/replay")
    public ResponseEntity<GraphEventView> replay(@PathVariable String eventId) {
        GraphEventView replay = operationsService.replay(eventId);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/operations/graph-events?eventId=" + replay.eventId()))
                .body(replay);
    }
}
