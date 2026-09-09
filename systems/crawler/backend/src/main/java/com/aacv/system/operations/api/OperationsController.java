package com.aacv.system.operations.api;

import com.aacv.system.operations.application.AlertService;
import com.aacv.system.operations.application.OperationsService;
import com.aacv.system.operations.domain.AlertStatus;
import com.aacv.system.operations.domain.AlertType;
import com.aacv.system.operations.domain.OperationsOverview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {

    private final OperationsService operationsService;
    private final AlertService alertService;

    public OperationsController(OperationsService operationsService, AlertService alertService) {
        this.operationsService = operationsService;
        this.alertService = alertService;
    }

    @GetMapping("/overview")
    public OperationsOverview overview() {
        return operationsService.overview();
    }

    @GetMapping("/alerts")
    public AlertEventPageResponse alerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return AlertEventPageResponse.from(alertService.findPage(status, type, page, size));
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    public AlertEventResponse acknowledge(
            @PathVariable @Min(1) long alertId,
            @Valid @RequestBody AlertAcknowledgeRequest request) {
        return AlertEventResponse.from(alertService.acknowledge(alertId, request.reason(), request.version()));
    }
}
