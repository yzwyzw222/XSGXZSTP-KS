package com.aacv.system.analytics.api;

import com.aacv.system.analytics.application.AnalyticsService;
import com.aacv.system.analytics.domain.AnalyticsQuery;
import com.aacv.system.source.domain.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public AnalyticsOverviewResponse overview(
            @RequestParam(required = false) Integer publicationYearFrom,
            @RequestParam(required = false) Integer publicationYearTo,
            @RequestParam(required = false) String achievementType,
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long topicId) {
        return AnalyticsOverviewResponse.from(service.overview(query(
                publicationYearFrom, publicationYearTo, achievementType, sourceType, organizationId, topicId)));
    }

    @GetMapping("/trends")
    public AnalyticsTrendResponse trends(
            @RequestParam(required = false) Integer publicationYearFrom,
            @RequestParam(required = false) Integer publicationYearTo,
            @RequestParam(required = false) String achievementType,
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long topicId) {
        return AnalyticsTrendResponse.from(service.trends(query(
                publicationYearFrom, publicationYearTo, achievementType, sourceType, organizationId, topicId)));
    }

    @GetMapping("/distributions")
    public AnalyticsDistributionResponse distributions(
            @RequestParam(required = false) Integer publicationYearFrom,
            @RequestParam(required = false) Integer publicationYearTo,
            @RequestParam(required = false) String achievementType,
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long topicId) {
        return AnalyticsDistributionResponse.from(service.distributions(query(
                publicationYearFrom, publicationYearTo, achievementType, sourceType, organizationId, topicId)));
    }

    @GetMapping("/collaboration")
    public AnalyticsCollaborationResponse collaboration(
            @RequestParam(required = false) Integer publicationYearFrom,
            @RequestParam(required = false) Integer publicationYearTo,
            @RequestParam(required = false) String achievementType,
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long topicId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return AnalyticsCollaborationResponse.from(service.collaboration(query(
                publicationYearFrom, publicationYearTo, achievementType, sourceType, organizationId, topicId), limit));
    }

    private AnalyticsQuery query(
            Integer publicationYearFrom,
            Integer publicationYearTo,
            String achievementType,
            SourceType sourceType,
            Long organizationId,
            Long topicId) {
        return new AnalyticsQuery(
                publicationYearFrom, publicationYearTo, achievementType, sourceType, organizationId, topicId);
    }
}
