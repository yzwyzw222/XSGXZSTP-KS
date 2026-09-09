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
@RequestMapping("/api/v1/catalog/achievements")
public class AchievementFieldOverrideController {

    private final GovernanceService service;

    public AchievementFieldOverrideController(GovernanceService service) {
        this.service = service;
    }

    @PostMapping("/{achievementId}/field-overrides")
    public FieldOverrideResponse overrideField(
            @PathVariable @Min(1) long achievementId,
            @Valid @RequestBody FieldOverrideRequest request) {
        return FieldOverrideResponse.from(service.overrideAchievementField(
                achievementId, request.fieldName(), request.value(),
                request.reason(), request.version()));
    }

    @PostMapping("/{achievementId}/field-overrides/{revisionId}/revert")
    public FieldOverrideResponse revertOverride(
            @PathVariable @Min(1) long achievementId,
            @PathVariable @Min(1) long revisionId,
            @Valid @RequestBody VersionReasonRequest request) {
        return FieldOverrideResponse.from(service.revertAchievementFieldOverride(
                achievementId, revisionId, request.reason(), request.version()));
    }
}
