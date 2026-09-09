package com.aacv.system.analytics.domain;

public record AnalyticsCollaborationItem(
        long leftId,
        String leftLabel,
        long rightId,
        String rightLabel,
        long sharedAchievementCount) {
}
