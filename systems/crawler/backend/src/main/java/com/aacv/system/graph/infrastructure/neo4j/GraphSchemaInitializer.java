package com.aacv.system.graph.infrastructure.neo4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
public class GraphSchemaInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphSchemaInitializer.class);
    private final Neo4jClient neo4jClient;
    private final GraphSchemaState state;

    GraphSchemaInitializer(Neo4jClient neo4jClient, GraphSchemaState state) {
        this.neo4jClient = neo4jClient;
        this.state = state;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureReady();
    }

    public boolean ensureReady() {
        if (state.isReady()) {
            return true;
        }
        try {
            String script = new ClassPathResource("neo4j/schema/V1__graph_schema.cypher")
                    .getContentAsString(StandardCharsets.UTF_8);
            for (String statement : script.split(";")) {
                if (!statement.isBlank()) {
                    neo4jClient.query(statement.trim()).run();
                }
            }
            state.markReady();
            return true;
        } catch (IOException | RuntimeException exception) {
            state.markUnavailable();
            LOGGER.error("Neo4j图约束初始化失败，图写入已停用，异常类型={}", exception.getClass().getSimpleName());
            return false;
        }
    }
}
