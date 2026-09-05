package com.aacv.system.infrastructure.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aacv.system.AacvSystemApplication;
import com.aacv.system.graph.application.port.GraphProjectionWriter;
import com.aacv.system.graph.application.GraphOutboxProcessor;
import com.aacv.system.graph.infrastructure.neo4j.Neo4jProjectionInspector;
import com.aacv.system.graph.infrastructure.persistence.MyBatisGraphSnapshotReader;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.images.builder.Transferable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OptimizationRecoveryIT {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void dependencyFaultsAndLogicalRestorePreserveBusinessDataAndRebuildGraph() throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        try (MySQLContainer source = pinPorts(new MySQLContainer("mysql:8.0.42").withDatabaseName("aacv_recovery_source"), 3306);
                Neo4jContainer graph = pinPorts(new Neo4jContainer("neo4j:5.26-community").withoutAuthentication(), 7473, 7474, 7687);
                MySQLContainer target = new MySQLContainer("mysql:8.0.42").withDatabaseName("aacv_recovery_target");
                Neo4jContainer restoredGraph = new Neo4jContainer("neo4j:5.26-community").withoutAuthentication()) {
            source.start();
            graph.start();
            String password = UUID.randomUUID() + "Aa1!";
            try (ConfigurableApplicationContext application = startApplication(source, graph);
                    HttpClient client = newClient(); HttpClient probe = newClient()) {
                JdbcTemplate jdbc = application.getBean(JdbcTemplate.class);
                seed(application, password);
                URI base = baseUri(application);
                login(client, base, password);
                List<Long> achievementIds = jdbc.queryForList("SELECT id FROM achievement ORDER BY id", Long.class);
                assertEquals(12, achievementIds.size());
                projectAndVerify(application, achievementIds);
                String graphPath = "/api/v1/graph/subgraph?centerType=ACHIEVEMENT&centerId=" + achievementIds.getFirst();
                assertStatus(client, base, graphPath, 200);

                // 仅停止本用例创建的容器；无论断言是否通过，都先恢复依赖再退出应用。
                stop(graph);
                try {
                    assertStatus(probe, base, "/actuator/health/liveness", 200);
                    assertStatus(probe, base, "/actuator/health/readiness", 200);
                    assertStatus(client, base, "/api/v1/catalog/achievements", 200);
                    long started = System.nanoTime();
                    assertStatus(client, base, graphPath, 503);
                    long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
                    assertTrue(elapsed < 20000, "图查询断连应在20秒内明确失败");
                    report.put("graphFailureMilliseconds", elapsed);
                    assertStatus(probe, base, "/actuator/health/graph", 503);
                } finally {
                    restart(graph);
                    awaitStatus(probe, base, "/actuator/health/graph", 200);
                }
                assertStatus(client, base, graphPath, 200);
                report.put("graphFaultRecovered", true);

                JsonNode csrf = JSON.readTree(get(client, base, "/api/v1/auth/csrf").body());
                long exportsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM export_task", Long.class);
                stop(source);
                try {
                    assertStatus(probe, base, "/actuator/health/liveness", 200);
                    assertStatus(probe, base, "/actuator/health/readiness", 503);
                    HttpResponse<String> rejected = post(client, base, "/api/v1/exports", csrf,
                            Map.of("format", "CSV", "filters", Map.of()));
                    assertTrue(rejected.statusCode() >= 400, "数据库断连不得成功接纳导出写入");
                    report.put("databaseWriteFailureStatus", rejected.statusCode());
                } finally {
                    restart(source);
                    awaitStatus(probe, base, "/actuator/health/readiness", 200);
                }
                assertEquals(exportsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM export_task", Long.class));
                assertStatus(client, base, "/api/v1/catalog/achievements", 200);
                report.put("databaseFaultRecovered", true);

                // 转储只包含隔离合成数据；口令留在容器环境，输出和验收报告不包含凭据。
                Map<String, Long> before = tableCounts(jdbc);
                String quotaBefore = quotaState(jdbc);
                assertEquals(0, source.execInContainer("sh", "-c",
                        "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysqldump --user=root --single-transaction "
                                + "--routines --triggers --hex-blob --set-gtid-purged=OFF --no-tablespaces "
                                + "aacv_recovery_source > /tmp/aacv-recovery.sql").getExitCode(), "隔离源数据库转储失败");
                byte[] dump = source.copyFileFromContainer("/tmp/aacv-recovery.sql", input -> input.readNBytes(64 * 1024 * 1024 + 1));
                assertTrue(dump.length > 0 && dump.length <= 64 * 1024 * 1024, "恢复样本转储大小异常");
                target.start();
                restoredGraph.start();
                JdbcTemplate restoredJdbc = new JdbcTemplate(new DriverManagerDataSource(
                        target.getJdbcUrl(), target.getUsername(), target.getPassword()));
                assertTrue(tableCounts(restoredJdbc).isEmpty(), "只允许导入本用例的全新空数据库");
                Instant recoveryStarted = Instant.now();
                target.copyFileToContainer(Transferable.of(dump), "/tmp/aacv-recovery.sql");
                assertEquals(0, target.execInContainer("sh", "-c",
                        "MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql --user=root aacv_recovery_target < /tmp/aacv-recovery.sql")
                        .getExitCode(), "隔离目标数据库导入失败");
                assertEquals(before, tableCounts(restoredJdbc), "所有业务及框架表的行数应与备份一致");
                assertEquals(quotaBefore, quotaState(restoredJdbc), "V12延后状态必须保持原值");
                assertEquals("恢复演练机构旧名称", restoredJdbc.queryForObject(
                        "SELECT display_name FROM organization_name_evidence", String.class));
                report.put("restoredTableCount", before.size());
                report.put("restoredAchievementCount", achievementIds.size());
                report.put("restoredOrganizationNameCount", 1);

                try (ConfigurableApplicationContext restored = startApplication(target, restoredGraph);
                        HttpClient restoredClient = newClient()) {
                    URI restoredBase = baseUri(restored);
                    login(restoredClient, restoredBase, password);
                    assertStatus(restoredClient, restoredBase, "/actuator/health/readiness", 200);
                    assertStatus(restoredClient, restoredBase, "/api/v1/catalog/achievements/" + achievementIds.getFirst(), 200);
                    assertTrue(!restored.getBean(Neo4jProjectionInspector.class).matches(
                            restored.getBean(MyBatisGraphSnapshotReader.class).load(achievementIds.getFirst()), 1),
                            "恢复目标图数据库必须从空投影开始");
                    rebuildAndVerify(restored, restoredClient, restoredBase, achievementIds);
                    assertStatus(restoredClient, restoredBase, graphPath, 200);
                    report.put("rebuiltProjectionCount", achievementIds.size());
                    report.put("syntheticRestoreAndRebuildMilliseconds", Duration.between(recoveryStarted, Instant.now()).toMillis());
                }
            }
        }
        report.put("recordedAt", Instant.now().toString());
        report.put("scope", "随机端口隔离合成样本；非生产RPO/RTO，也不替代交互式备份脚本的ACL和保留策略验证");
        Files.writeString(Path.of("target", "optimization-recovery.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

    private ConfigurableApplicationContext startApplication(MySQLContainer mysql, Neo4jContainer neo4j) {
        return new SpringApplicationBuilder(AacvSystemApplication.class).run(
                "--spring.config.import=optional:classpath:optimization-no-local.properties",
                "--spring.datasource.url=" + mysql.getJdbcUrl(), "--spring.datasource.username=" + mysql.getUsername(),
                "--spring.datasource.password=" + mysql.getPassword(), "--spring.neo4j.uri=" + neo4j.getBoltUrl(),
                "--spring.neo4j.authentication.username=", "--spring.neo4j.authentication.password=",
                "--server.address=127.0.0.1", "--server.port=0", "--aacv.bootstrap-admin.enabled=false",
                "--spring.quartz.auto-startup=false", "--aacv.graph.outbox.enabled=false",
                "--aacv.operations.alerts-enabled=false", "--logging.level.root=WARN");
    }

    private void seed(ConfigurableApplicationContext application, String password) throws Exception {
        JdbcTemplate jdbc = application.getBean(JdbcTemplate.class);
        jdbc.update("INSERT INTO sys_user (id,username,password_hash,status) VALUES (9001,?,?, 'ACTIVE')",
                "optimization-recovery-admin", application.getBean(PasswordEncoder.class).encode(password));
        jdbc.update("INSERT INTO sys_user_role (user_id,role_id) VALUES (9001,1)");
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isRegularFile(directory.resolve("tools/development/rendering-sample-data.sql"))) {
            directory = directory.getParent();
        }
        if (directory == null) throw new IllegalStateException("未找到隔离恢复样本脚本");
        try (var connection = application.getBean(DataSource.class).getConnection(); var statement = connection.createStatement()) {
            statement.execute("SET @aacv_demo_actor_id = 9001");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(directory.resolve("tools/development/rendering-sample-data.sql")));
        }
        jdbc.update("""
                INSERT INTO organization_name_evidence
                (organization_id,source_id,name_hash,display_name,first_observed_at,last_observed_at)
                SELECT MIN(id), (SELECT MIN(id) FROM data_source), SHA2('recovery-alias',256),
                       '恢复演练机构旧名称', '2026-01-01 00:00:00', '2026-09-01 00:00:00' FROM organization
                """);
        jdbc.update("""
                UPDATE crawl_run SET status='PAUSED', completion_reason='QUOTA_EXHAUSTED', quota_deferrals=2,
                    deferred_until='2099-01-01 00:00:00' ORDER BY id LIMIT 1
                """);
    }

    private void projectAndVerify(ConfigurableApplicationContext application, List<Long> ids) {
        GraphProjectionWriter writer = application.getBean(GraphProjectionWriter.class);
        MyBatisGraphSnapshotReader reader = application.getBean(MyBatisGraphSnapshotReader.class);
        Neo4jProjectionInspector inspector = application.getBean(Neo4jProjectionInspector.class);
        JdbcTemplate jdbc = application.getBean(JdbcTemplate.class);
        for (long id : ids) {
            long version = jdbc.queryForObject("SELECT desired_version FROM graph_projection_state WHERE achievement_id=?", Long.class, id);
            writer.projectAchievement(id, version);
            assertTrue(inspector.matches(reader.load(id), version), "图投影应与MySQL快照一致，成果ID=" + id);
        }
    }

    private void rebuildAndVerify(ConfigurableApplicationContext application, HttpClient client, URI base, List<Long> ids) throws Exception {
        JsonNode rebuilt = runMaintenance(client, base, "rebuild", Map.of("confirmation", "REBUILD_AACV_MANAGED_GRAPH"));
        assertEquals(ids.size(), rebuilt.get("scannedCount").asInt());
        GraphOutboxProcessor processor = application.getBean(GraphOutboxProcessor.class);
        for (int batch = 0; batch < 10 && processor.processBatch() > 0; batch++) { }
        Neo4jProjectionInspector inspector = application.getBean(Neo4jProjectionInspector.class);
        MyBatisGraphSnapshotReader reader = application.getBean(MyBatisGraphSnapshotReader.class);
        JdbcTemplate jdbc = application.getBean(JdbcTemplate.class);
        for (long id : ids) {
            long version = jdbc.queryForObject("SELECT desired_version FROM graph_projection_state WHERE achievement_id=?", Long.class, id);
            assertTrue(inspector.matches(reader.load(id), version), "恢复后的投影与规范版本必须一致，成果ID=" + id);
        }
        // 样本含中断对账；先恢复并保留历史差异，再用新的全范围运行验证零差异。
        JsonNode resumed = runMaintenance(client, base, "reconcile", Map.of());
        assertEquals(ids.size(), resumed.get("scannedCount").asInt());
        assertEquals(1, resumed.get("differenceCount").asInt(), "恢复旧对账应保留已记录的差异");
        JsonNode reconciled = runMaintenance(client, base, "reconcile", Map.of());
        assertEquals(ids.size(), reconciled.get("scannedCount").asInt());
        assertEquals(0, reconciled.get("differenceCount").asInt(), "恢复图谱应通过零差异对账");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM graph_projection_state WHERE applied_version <> desired_version", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM graph_outbox_event WHERE status IN ('PENDING','PROCESSING')", Integer.class));
    }

    private JsonNode runMaintenance(HttpClient client, URI base, String action, Map<String, Object> body) throws Exception {
        JsonNode csrf = JSON.readTree(get(client, base, "/api/v1/auth/csrf").body());
        HttpResponse<String> response = post(client, base, "/api/v1/operations/graph-maintenance/" + action, csrf, body);
        assertEquals(202, response.statusCode());
        long id = JSON.readTree(response.body()).get("id").asLong();
        Instant deadline = Instant.now().plusSeconds(60);
        String lastProgress = "未读取到运行";
        do {
            HttpResponse<String> page = get(client, base, "/api/v1/operations/graph-maintenance/runs?page=0&size=100");
            assertEquals(200, page.statusCode());
            for (JsonNode run : JSON.readTree(page.body()).get("items")) {
                if (run.get("id").asLong() != id) continue;
                String status = run.get("status").asText();
                lastProgress = status + ", scannedCount=" + run.get("scannedCount").asLong();
                assertTrue(!"FAILED".equals(status), "图维护失败：" + run.get("errorCode").asText());
                if ("SUCCEEDED".equals(status)) return run;
            }
            Thread.sleep(100);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("图维护运行超时：" + action + ", " + lastProgress);
    }

    private Map<String, Long> tableCounts(JdbcTemplate jdbc) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name", String.class)) {
            if (!table.matches("[A-Za-z0-9_]+")) throw new IllegalStateException("隔离数据库表名无效");
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Long.class));
        }
        return counts;
    }

    private String quotaState(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT CONCAT(status,'|',completion_reason,'|',quota_deferrals,'|',deferred_until) "
                + "FROM crawl_run ORDER BY id LIMIT 1", String.class);
    }

    private HttpClient newClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();
    }

    private URI baseUri(ConfigurableApplicationContext application) {
        return URI.create("http://127.0.0.1:" + application.getEnvironment().getRequiredProperty("local.server.port"));
    }

    private void login(HttpClient client, URI base, String password) throws Exception {
        HttpResponse<String> response = get(client, base, "/api/v1/auth/csrf");
        assertEquals(200, response.statusCode());
        assertEquals(200, post(client, base, "/api/v1/auth/login", JSON.readTree(response.body()),
                Map.of("username", "optimization-recovery-admin", "password", password)).statusCode());
    }

    private HttpResponse<String> get(HttpClient client, URI base, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, URI base, String path, JsonNode csrf, Map<String, Object> body) throws Exception {
        return client.send(HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json").header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertStatus(HttpClient client, URI base, String path, int expected) throws Exception {
        assertEquals(expected, get(client, base, path).statusCode(), path);
    }

    private void awaitStatus(HttpClient client, URI base, String path, int expected) throws Exception {
        Instant deadline = Instant.now().plusSeconds(60);
        int status;
        do {
            status = get(client, base, path).statusCode();
            if (status == expected) return;
            Thread.sleep(500);
        } while (Instant.now().isBefore(deadline));
        assertEquals(expected, status, "依赖恢复超时：" + path);
    }

    private void stop(GenericContainer<?> container) {
        DockerClientFactory.instance().client().stopContainerCmd(container.getContainerId()).withTimeout(5).exec();
    }

    private void restart(GenericContainer<?> container) {
        DockerClientFactory.instance().client().startContainerCmd(container.getContainerId()).exec();
        var bindings = DockerClientFactory.instance().client().inspectContainerCmd(container.getContainerId())
                .exec().getNetworkSettings().getPorts().getBindings();
        for (int port : container.getExposedPorts()) {
            assertEquals(container.getMappedPort(port).toString(), bindings.get(ExposedPort.tcp(port))[0].getHostPortSpec(),
                    "故障恢复必须保持依赖地址不变");
        }
    }

    private <T extends GenericContainer<?>> T pinPorts(T container, int... ports) throws Exception {
        // 预选空闲本地端口，避免Docker重启时重新分配随机映射而改变应用连接地址。
        List<PortBinding> bindings = new ArrayList<>();
        for (int port : ports) {
            try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
                bindings.add(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", socket.getLocalPort()), ExposedPort.tcp(port)));
            }
        }
        container.withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(bindings));
        return container;
    }
}
