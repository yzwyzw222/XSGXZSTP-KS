package com.aacv.system.governance.api;

import com.aacv.system.governance.application.GovernanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/merge-decisions")
public class MergeDecisionController {

    private final GovernanceService service;

    public MergeDecisionController(GovernanceService service) {
        this.service = service;
    }

    @PostMapping("/{decisionId}/revert")
    public MergeDecisionResponse revertDecision(
            @PathVariable @Min(1) long decisionId,
            @Valid @RequestBody VersionReasonRequest request) {
        return MergeDecisionResponse.from(
                service.revertDecision(decisionId, request.reason(), request.version()));
    }
}
