package com.aacv.system.analytics.api;

import com.aacv.system.analytics.domain.AnalyticsCollaboration;
import com.aacv.system.analytics.domain.AnalyticsCollaborationItem;
import com.aacv.system.analytics.domain.AnalyticsSnapshot;
import java.time.Instant;
import java.util.List;

public record AnalyticsCollaborationResponse(
        List<AnalyticsCollaborationItem> authors,
        List<AnalyticsCollaborationItem> organizations,
        AnalyticsScopeResponse scope,
        Instant updatedAt) {

    static AnalyticsCollaborationResponse from(AnalyticsSnapshot<AnalyticsCollaboration> snapshot) {
        AnalyticsCollaboration value = snapshot.value();
        return new AnalyticsCollaborationResponse(
                value.authors(), value.organizations(), AnalyticsScopeResponse.mysql(snapshot.filters()),
                snapshot.updatedAt());
    }
}
