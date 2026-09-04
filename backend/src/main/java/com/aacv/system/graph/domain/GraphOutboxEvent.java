package com.aacv.system.graph.domain;

public record GraphOutboxEvent(
        long id,
        String eventId,
        long achievementId,
        long desiredVersion,
        int attempts) {
}
