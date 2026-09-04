package com.aacv.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.infrastructure.database.DatabaseProbeMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AacvSystemApplicationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private DatabaseProbeMapper databaseProbeMapper;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Driver neo4jDriver;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @LocalServerPort
    private int port;

    @Test
    void startsWithIsolatedInfrastructureAndExposesHealthGroups() throws Exception {
        assertEquals(1, databaseProbeMapper.selectOne());
        assertEquals(11, flyway.info().applied().length);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        neo4jDriver.verifyConnectivity();
        assertInstanceOf(JdbcTransactionManager.class, transactionManager);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        assertHealthGroupUp(client, "liveness");
        assertHealthGroupUp(client, "readiness");
        assertHealthGroupUp(client, "graph");
    }

    private void assertHealthGroupUp(HttpClient client, String group) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/actuator/health/" + group))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), group);
        assertNotNull(response.body(), group);
        assertTrue(response.body().contains("\"status\":\"UP\""), group);
    }

}
