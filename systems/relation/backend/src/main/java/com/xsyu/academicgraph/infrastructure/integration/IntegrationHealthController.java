package com.xsyu.academicgraph.infrastructure.integration;

import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本地整合就绪探针只返回状态，不暴露连接信息或异常详情。 */
@RestController
@Profile("integration")
public class IntegrationHealthController {
    private final DataSource dataSource;
    private final Driver driver;

    public IntegrationHealthController(DataSource dataSource, Driver driver) {
        this.dataSource = dataSource;
        this.driver = driver;
    }

    @GetMapping("/api/integration/health")
    public ResponseEntity<Map<String, String>> health() {
        try (var connection = dataSource.getConnection()) {
            if (!connection.isValid(3)) {
                return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
            }
            driver.verifyConnectivity();
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (SQLException | RuntimeException failure) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        }
    }
}

