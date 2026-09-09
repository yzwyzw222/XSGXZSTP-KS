package com.aacv.system.analytics.domain;

import java.time.Instant;

public record AnalyticsSnapshot<T>(T value, AnalyticsQuery filters, Instant updatedAt) {
}
