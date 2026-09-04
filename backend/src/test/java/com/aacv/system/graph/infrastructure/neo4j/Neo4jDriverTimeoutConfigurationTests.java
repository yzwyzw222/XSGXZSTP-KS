package com.aacv.system.graph.infrastructure.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.neo4j.autoconfigure.Neo4jProperties;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class Neo4jDriverTimeoutConfigurationTests {

    @Test
    void boundsDriverConnectionAcquisitionAndRetryTimeouts() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        for (PropertySource<?> source : sources) {
            environment.getPropertySources().addLast(source);
        }

        Neo4jProperties properties = Binder.get(environment)
                .bind("spring.neo4j", Neo4jProperties.class)
                .orElseThrow(() -> new AssertionError("Neo4j配置未绑定"));

        assertEquals(Duration.ofSeconds(5), properties.getConnectionTimeout());
        assertEquals(Duration.ofSeconds(5), properties.getMaxTransactionRetryTime());
        assertEquals(Duration.ofSeconds(5), properties.getPool().getConnectionAcquisitionTimeout());
    }
}
