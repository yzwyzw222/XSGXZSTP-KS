package com.aacv.system.graph.infrastructure.neo4j;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("graphSchema")
public class GraphSchemaState implements HealthIndicator {

    private final AtomicBoolean ready = new AtomicBoolean();

    void markReady() {
        ready.set(true);
    }

    void markUnavailable() {
        ready.set(false);
    }

    public boolean isReady() {
        return ready.get();
    }

    @Override
    public Health health() {
        return ready.get() ? Health.up().build() : Health.down().build();
    }
}
