package com.aacv.system.governance.api;

import com.aacv.system.governance.application.GovernanceService;
import com.aacv.system.governance.domain.CandidateStatus;
import com.aacv.system.governance.domain.DuplicateCandidateQuery;
import com.aacv.system.governance.domain.GovernedEntityType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/duplicate-candidates")
public class DuplicateCandidateController {

    private final GovernanceService service;

    public DuplicateCandidateController(GovernanceService service) {
        this.service = service;
    }

    @GetMapping
    public DuplicateCandidatePageResponse findCandidates(
            @RequestParam(required = false) GovernedEntityType entityType,
            @RequestParam(required = false) CandidateStatus status,
            @RequestParam(required = false) @Min(1) Long sourceId,
            @RequestParam(required = false) @Min(1) Integer ruleVersion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return DuplicateCandidatePageResponse.from(service.findCandidates(
                new DuplicateCandidateQuery(
                        entityType, status, sourceId, ruleVersion, createdFrom, createdTo),
                page, size));
    }

    @GetMapping("/{candidateId}")
    public DuplicateCandidateResponse findCandidate(@PathVariable @Min(1) long candidateId) {
        return DuplicateCandidateResponse.from(service.requireCandidate(candidateId));
    }

    @PostMapping("/{candidateId}/accept")
    public MergeDecisionResponse acceptCandidate(
            @PathVariable @Min(1) long candidateId,
            @Valid @RequestBody CandidateAcceptRequest request) {
        return MergeDecisionResponse.from(service.acceptCandidate(
                candidateId, request.canonicalEntityId(), request.reason(), request.version()));
    }

    @PostMapping("/{candidateId}/reject")
    public MergeDecisionResponse rejectCandidate(
            @PathVariable @Min(1) long candidateId,
            @Valid @RequestBody VersionReasonRequest request) {
        return MergeDecisionResponse.from(
                service.rejectCandidate(candidateId, request.reason(), request.version()));
    }
}
