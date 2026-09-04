package com.aacv.system.analytics.domain;

import java.util.List;

public record AnalyticsCollaboration(
        List<AnalyticsCollaborationItem> authors,
        List<AnalyticsCollaborationItem> organizations) {

    public AnalyticsCollaboration {
        authors = List.copyOf(authors);
        organizations = List.copyOf(organizations);
    }
}
