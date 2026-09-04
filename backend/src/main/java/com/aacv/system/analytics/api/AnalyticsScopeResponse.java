package com.aacv.system.analytics.api;

import com.aacv.system.analytics.domain.AnalyticsQuery;

public record AnalyticsScopeResponse(String source, AnalyticsQuery filters) {

    static AnalyticsScopeResponse mysql(AnalyticsQuery filters) {
        return new AnalyticsScopeResponse("MYSQL", filters);
    }
}
