package com.aacv.system.graph.api;

import com.aacv.system.graph.application.GraphMaintenanceService;
import com.aacv.system.graph.domain.GraphMaintenanceRun;
import com.aacv.system.graph.domain.GraphMaintenanceType;
import com.aacv.system.shared.domain.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/operations/graph-maintenance")
public class GraphMaintenanceController {

    private final GraphMaintenanceService service;

    public GraphMaintenanceController(GraphMaintenanceService service) {
        this.service = service;
    }

    @GetMapping("/runs")
    public PageResult<GraphMaintenanceRun> runs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.runs(page, size);
    }

    @PostMapping("/backfill")
    public ResponseEntity<GraphMaintenanceRun> backfill() {
        return ResponseEntity.accepted().body(service.start(GraphMaintenanceType.INITIAL_BACKFILL));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<GraphMaintenanceRun> reconcile() {
        return ResponseEntity.accepted().body(service.start(GraphMaintenanceType.RECONCILE));
    }

    @PostMapping("/rebuild")
    public ResponseEntity<GraphMaintenanceRun> rebuild(@Valid @RequestBody RebuildRequest request) {
        if (!"REBUILD_AACV_MANAGED_GRAPH".equals(request.confirmation())) {
            throw new IllegalArgumentException("全量重建确认值无效");
        }
        return ResponseEntity.accepted().body(service.start(GraphMaintenanceType.FULL_REBUILD));
    }

    public record RebuildRequest(@NotBlank String confirmation) {
    }
}
