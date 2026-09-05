package com.aacv.system.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.aacv.system.identity.application.UpdateUserCommand;
import com.aacv.system.identity.domain.UserProfile;
import com.aacv.system.identity.domain.UserStatus;
import com.aacv.system.identity.infrastructure.security.UserPrincipal;
import com.aacv.system.shared.application.ResourceConflictException;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aacv.system.identity.application.CreateUserCommand;
import com.aacv.system.export.application.port.ExportTaskDispatcher;
import com.aacv.system.identity.application.UserAccountService;
import com.aacv.system.identity.application.VersionConflictException;
import com.aacv.system.identity.domain.RoleCode;
import com.aacv.system.identity.domain.UserAccount;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(properties = "aacv.operations.alerts-enabled=false")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "/sql/reset-identity.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SecurityIntegrationTests {

    private static final String ADMIN_USERNAME = "stage2-admin";
    private static final String ADMIN_PASSWORD = "Admin-Test-Password-123!";
    private static final String USER_PASSWORD = "User-Test-Password-123!";
    private static final String NEW_USER_PASSWORD = "New-User-Password-456!";

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.42")
            .withDatabaseName("aacv_security_test");

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withoutAuthentication();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.aacv.system.operations.application.port.AuditLogRepository auditLogRepository;

    @Autowired
    private com.aacv.system.operations.application.AuditService auditService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @MockitoBean
    private ExportTaskDispatcher exportTaskDispatcher;

    @BeforeEach
    void createInitialAdministrator() {
        userAccountService.bootstrapInitialAdministrator(
                new CreateUserCommand(ADMIN_USERNAME, ADMIN_PASSWORD, Set.of(RoleCode.ADMIN)));
    }

    @Test
    void loginMeAndLogoutUseServerSessionAndCsrf() throws Exception {
        AuthSession admin = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(admin.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("CSRF_INVALID"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(admin.cookie())
                        .header(admin.csrfHeader(), admin.csrfToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(admin.cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void anonymousAndNonAdministratorCannotAccessUserManagement() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));

        userAccountService.createUser(
                new CreateUserCommand("research-user", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        AuthSession researcher = login("research-user", USER_PASSWORD);

        mockMvc.perform(get("/api/v1/users").cookie(researcher.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void stageThreeCatalogCrawlAndCsrfBoundariesFollowRolePolicy() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/achievements"))
                .andExpect(status().isUnauthorized());

        userAccountService.createUser(
                new CreateUserCommand("stage3-researcher", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        AuthSession researcher = login("stage3-researcher", USER_PASSWORD);
        mockMvc.perform(get("/api/v1/catalog/achievements").cookie(researcher.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/v1/crawl/tasks").cookie(researcher.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        userAccountService.createUser(
                new CreateUserCommand("stage3-operator", USER_PASSWORD, Set.of(RoleCode.DATA_OPERATOR)));
        AuthSession operator = login("stage3-operator", USER_PASSWORD);
        mockMvc.perform(get("/api/v1/crawl/tasks").cookie(operator.cookie()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/crawl/runs/1/cancel").cookie(operator.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CSRF_INVALID"));
        mockMvc.perform(post("/api/v1/crawl/runs/1/cancel")
                        .cookie(operator.cookie())
                        .header(operator.csrfHeader(), operator.csrfToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void stageFourGovernanceAndQualityBoundariesFollowRoleAndCsrfPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/duplicate-candidates"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/quality-metrics"))
                .andExpect(status().isUnauthorized());

        userAccountService.createUser(
                new CreateUserCommand("stage4-researcher", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        AuthSession researcher = login("stage4-researcher", USER_PASSWORD);
        mockMvc.perform(get("/api/v1/duplicate-candidates/1/comparison"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/catalog/authors/1/evidence"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/duplicate-candidates/1/comparison").cookie(researcher.cookie()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/catalog/authors/1/evidence").cookie(researcher.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/duplicate-candidates").cookie(researcher.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/quality-metrics").cookie(researcher.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/catalog/achievements/1/field-overrides")
                        .cookie(researcher.cookie())
                        .header(researcher.csrfHeader(), researcher.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fieldName", "title",
                                "value", "越权标题",
                                "reason", "权限边界测试",
                                "version", 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        userAccountService.createUser(
                new CreateUserCommand("stage4-operator", USER_PASSWORD, Set.of(RoleCode.DATA_OPERATOR)));
        AuthSession operator = login("stage4-operator", USER_PASSWORD);
        mockMvc.perform(get("/api/v1/duplicate-candidates/1/comparison").cookie(operator.cookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/duplicate-candidates").cookie(operator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/v1/quality-metrics").cookie(operator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(post("/api/v1/duplicate-candidates/1/accept")
                        .cookie(operator.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "canonicalEntityId", 1,
                                "reason", "CSRF边界测试",
                                "version", 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CSRF_INVALID"));
    }

    @Test
    void stageFiveGraphBoundariesRejectAnonymousUnauthorizedAndMissingCsrfRequests() throws Exception {
        mockMvc.perform(get("/api/v1/graph/subgraph")
                        .queryParam("centerType", "ACHIEVEMENT")
                        .queryParam("centerId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));

        userAccountService.createUser(
                new CreateUserCommand("stage5-researcher", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        AuthSession researcher = login("stage5-researcher", USER_PASSWORD);
        mockMvc.perform(get("/api/v1/graph/sync-status").cookie(researcher.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        userAccountService.createUser(
                new CreateUserCommand("stage5-operator", USER_PASSWORD, Set.of(RoleCode.DATA_OPERATOR)));
        AuthSession operator = login("stage5-operator", USER_PASSWORD);
        mockMvc.perform(post("/api/v1/operations/graph-maintenance/reconcile")
                        .cookie(operator.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CSRF_INVALID"));
        mockMvc.perform(post("/api/v1/operations/graph-maintenance/rebuild")
                        .cookie(operator.cookie())
                        .header(operator.csrfHeader(), operator.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("confirmation", "REBUILD_AACV_MANAGED_GRAPH"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void stageSevenBoundariesRejectAnonymousUnauthorizedAndMissingCsrfRequests() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));

        userAccountService.createUser(
                new CreateUserCommand("stage7-researcher", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        AuthSession researcher = login("stage7-researcher", USER_PASSWORD);
        mockMvc.perform(get("/api/v1/analytics/overview").cookie(researcher.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achievementCount").value(0))
                .andExpect(jsonPath("$.scope.source").value("MYSQL"));
        mockMvc.perform(get("/api/v1/operations/overview").cookie(researcher.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/exports")
                        .cookie(researcher.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("format", "CSV", "filters", Map.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CSRF_INVALID"));

        MvcResult exportResult = mockMvc.perform(post("/api/v1/exports")
                        .cookie(researcher.cookie())
                        .header(researcher.csrfHeader(), researcher.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("format", "CSV", "filters", Map.of()))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedCount").value(0))
                .andExpect(jsonPath("$.downloadAvailable").value(false))
                .andExpect(jsonPath("$.downloadToken").isEmpty())
                .andReturn();
        String exportId = objectMapper.readTree(exportResult.getResponse().getContentAsString()).get("id").asString();
        mockMvc.perform(get("/api/v1/exports/{exportId}", exportId).cookie(researcher.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(exportId));

        userAccountService.createUser(
                new CreateUserCommand("stage7-operator", USER_PASSWORD, Set.of(RoleCode.DATA_OPERATOR)));
        AuthSession operator = login("stage7-operator", USER_PASSWORD);
        mockMvc.perform(get("/api/v1/exports/{exportId}", exportId).cookie(operator.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/operations/alerts").cookie(operator.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        AuthSession administrator = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        mockMvc.perform(post("/api/v1/operations/alerts/1/acknowledge")
                        .cookie(administrator.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "CSRF边界测试", "version", 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CSRF_INVALID"));
    }

    @Test
    void administratorCanReadOperationsAcknowledgeAlertAndProduceAudit() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO alert_event (
                    alert_type, severity, status, subject_type, subject_id, dedup_key,
                    summary, evidence_json, detected_signal_at, first_detected_at, last_detected_at
                ) VALUES (
                    'CRAWL_CONSECUTIVE_FAILURES', 'WARNING', 'OPEN', 'SOURCE', '1',
                    'CRAWL_CONSECUTIVE_FAILURES:SOURCE:1', '测试数据源连续失败', JSON_OBJECT('failureCount', 3),
                    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                )
                """);
        Long alertId = jdbcTemplate.queryForObject("SELECT id FROM alert_event", Long.class);
        assertNotNull(alertId);

        AuthSession administrator = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/v1/operations/overview").cookie(administrator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationStatus").value("UP"))
                .andExpect(jsonPath("$.mysqlStatus").value("UP"))
                .andExpect(jsonPath("$.openAlertCount").value(1));
        mockMvc.perform(get("/api/v1/operations/alerts")
                        .cookie(administrator.cookie())
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(alertId))
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].detectedSignalAt").doesNotExist());

        mockMvc.perform(post("/api/v1/operations/alerts/{alertId}/acknowledge", alertId)
                        .cookie(administrator.cookie())
                        .header(administrator.csrfHeader(), administrator.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "已确认并转交处置", "version", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgementReason").value("已确认并转交处置"))
                .andExpect(jsonPath("$.version").value(1));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'ALERT_ACKNOWLEDGED' "
                                + "AND target_type = 'ALERT_EVENT' AND target_id = ? AND result = 'SUCCESS'",
                        Integer.class,
                        Long.toString(alertId)));
    }

    @Test
    void invalidCredentialsAndDisabledAccountUseSameSafeResponseAndAreAudited(CapturedOutput output)
            throws Exception {
        UserAccount disabled = userAccountService.createUser(
                new CreateUserCommand("disabled-user", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        userAccountService.disableUser(disabled.id(), disabled.version());

        CsrfSession csrf = csrfSession();
        MvcResult wrongPassword = loginAttempt(csrf, ADMIN_USERNAME, "Wrong-Password-789!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andReturn();
        MvcResult missingUser = loginAttempt(csrf, "missing-user", USER_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult disabledUser = loginAttempt(csrf, "disabled-user", USER_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn();

        String wrongDetail = objectMapper.readTree(wrongPassword.getResponse().getContentAsString()).get("detail").asString();
        String missingDetail = objectMapper.readTree(missingUser.getResponse().getContentAsString()).get("detail").asString();
        String disabledDetail = objectMapper.readTree(disabledUser.getResponse().getContentAsString()).get("detail").asString();
        assertEquals(wrongDetail, missingDetail);
        assertEquals(wrongDetail, disabledDetail);
        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'LOGIN_FAILED' AND result = 'FAILURE'",
                        Integer.class));

        String combinedResponses = wrongPassword.getResponse().getContentAsString()
                + missingUser.getResponse().getContentAsString()
                + disabledUser.getResponse().getContentAsString();
        assertFalse(combinedResponses.contains(USER_PASSWORD));
        assertFalse(combinedResponses.contains("Wrong-Password-789!"));
        assertFalse(combinedResponses.contains("java."));
        assertFalse(combinedResponses.toLowerCase().contains("select "));
        assertFalse(output.getAll().contains(USER_PASSWORD));
        assertFalse(output.getAll().contains("Wrong-Password-789!"));
    }

    @Test
    void csrfIsRequiredAndExternalTraceIdIsValidated() throws Exception {
        MvcResult missingCsrf = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CSRF_INVALID"))
                .andReturn();

        CsrfSession csrf = csrfSession();
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.csrfHeader(), "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CSRF_INVALID"));

        MvcResult unsafeTrace = mockMvc.perform(get("/api/v1/users")
                        .header("X-Trace-Id", "unsafe trace value"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String responseTrace = unsafeTrace.getResponse().getHeader("X-Trace-Id");
        assertNotNull(responseTrace);
        assertNotEquals("unsafe trace value", responseTrace);
        assertTrue(responseTrace.matches("[0-9a-f]{32}"));
        assertEquals(
                responseTrace,
                objectMapper.readTree(unsafeTrace.getResponse().getContentAsString()).get("traceId").asString());
        assertFalse(missingCsrf.getResponse().getContentAsString().contains(ADMIN_PASSWORD));
    }

    @Test
    void userManagementRejectsEmptyAndOversizedInputs() throws Exception {
        AuthSession admin = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/v1/users")
                        .cookie(admin.cookie())
                        .header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "",
                                "password", "",
                                "roles", Set.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/users")
                        .cookie(admin.cookie())
                        .header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "a".repeat(65),
                                "password", "A".repeat(129),
                                "roles", Set.of("RESEARCHER")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void administratorCanManageUsersWithValidationConflictAndAuditBoundaries() throws Exception {
        AuthSession admin = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String createBody = json(Map.of(
                "username", "managed-user",
                "password", USER_PASSWORD,
                "roles", Set.of("DATA_OPERATOR")));

        MvcResult createdResult = mockMvc.perform(post("/api/v1/users")
                        .cookie(admin.cookie())
                        .header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("managed-user"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        long userId = created.get("id").longValue();

        mockMvc.perform(post("/api/v1/users")
                        .cookie(admin.cookie())
                        .header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_CONFLICT"));

        mockMvc.perform(post("/api/v1/users/{userId}/disable", userId)
                        .cookie(admin.cookie())
                        .header(admin.csrfHeader(), admin.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", 99))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VERSION_CONFLICT"));

        mockMvc.perform(get("/api/v1/users").cookie(admin.cookie()).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/users").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].username").value("managed-user"))
                .andExpect(jsonPath("$.items[1].roles[0]").value("DATA_OPERATOR"));

        mockMvc.perform(get("/api/v1/operations/audits").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").exists());

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_log "
                                + "WHERE action = 'USER_CREATED' AND target_id = ? AND result = 'SUCCESS' "
                                + "AND actor_user_id IS NOT NULL",
                        Integer.class,
                        Long.toString(userId)));
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_log "
                                + "WHERE action = 'USER_DISABLED' AND target_id = ? AND result = 'SUCCESS'",
                        Integer.class,
                        Long.toString(userId)));
        String summaries = jdbcTemplate.queryForObject(
                "SELECT GROUP_CONCAT(summary_json) FROM audit_log", String.class);
        assertNotNull(summaries);
        assertFalse(summaries.contains(USER_PASSWORD));
    }

    @Test
    void passwordResetAndDisableInvalidateExistingSessions() throws Exception {
        UserAccount user = userAccountService.createUser(
                new CreateUserCommand("session-user", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        AuthSession originalSession = login("session-user", USER_PASSWORD);

        UserAccount reset = userAccountService.resetPassword(user.id(), user.version(), NEW_USER_PASSWORD);
        mockMvc.perform(get("/api/v1/auth/me").cookie(originalSession.cookie()))
                .andExpect(status().isUnauthorized());

        AuthSession resetSession = login("session-user", NEW_USER_PASSWORD);
        userAccountService.disableUser(reset.id(), reset.version());
        mockMvc.perform(get("/api/v1/auth/me").cookie(resetSession.cookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredServerSessionCannotAccessProtectedEndpoint() throws Exception {
        AuthSession admin = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        assertTrue(jdbcTemplate.update(
                        "DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", ADMIN_USERNAME)
                > 0);

        mockMvc.perform(get("/api/v1/auth/me").cookie(admin.cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void concurrentUpdatesWithSameVersionAllowExactlyOneWinner() throws Exception {
        UserAccount user = userAccountService.createUser(
                new CreateUserCommand("concurrent-user", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Class<?>> enable = () -> updateResult(ready, start, () -> userAccountService.updateUser(user.id(), new UpdateUserCommand(0,
                    new UserProfile("并发资料", null, null, null, null, null), user.roles(), user.status())));
            Callable<Class<?>> disable = () -> updateResult(ready, start, () -> userAccountService.disableUser(user.id(), 0));
            Future<Class<?>> first = executor.submit(enable);
            Future<Class<?>> second = executor.submit(disable);
            ready.await();
            start.countDown();

            Set<Class<?>> results = Set.of(first.get(), second.get());
            assertEquals(Set.of(Void.class, VersionConflictException.class), results);
            assertEquals(1, userAccountService.getById(user.id()).version());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void unifiedProfileEditKeepsSessionAndSecurityEditInvalidatesIt() throws Exception {
        AuthSession admin = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        UserAccount user = userAccountService.createUser(new CreateUserCommand("profile-user", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        AuthSession loggedIn = login(user.username().value(), USER_PASSWORD);
        Map<String, Object> body = editBody(user, "资料用户");
        mockMvc.perform(put("/api/v1/users/" + user.id()).cookie(admin.cookie())
                        .header(admin.csrfHeader(), admin.csrfToken()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.realName").value("资料用户"))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(get("/api/v1/auth/me").cookie(loggedIn.cookie())).andExpect(status().isOk());
        UserAccount updated = userAccountService.getById(user.id());
        assertEquals(0, updated.securityVersion());
        userAccountService.updateUser(user.id(), new UpdateUserCommand(updated.version(), updated.profile(),
                Set.of(RoleCode.DATA_OPERATOR), UserStatus.ACTIVE));
        assertEquals(1, userAccountService.getById(user.id()).securityVersion());
        mockMvc.perform(get("/api/v1/auth/me").cookie(loggedIn.cookie())).andExpect(status().isUnauthorized());
    }

    @Test
    void unchangedEditDoesNotAdvanceVersionAndStaleEditKeepsAllFields() throws Exception {
        UserAccount user = userAccountService.createUser(new CreateUserCommand("unchanged-user", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        var noChange = new UpdateUserCommand(0, user.profile(), user.roles(), user.status());
        assertEquals(0, userAccountService.updateUser(user.id(), noChange).version());
        UserAccount changed = userAccountService.updateUser(user.id(), new UpdateUserCommand(0,
                new UserProfile("已更新", null, null, "测试单位", null, null), user.roles(), user.status()));
        assertThrows(VersionConflictException.class, () -> userAccountService.updateUser(user.id(),
                new UpdateUserCommand(0, UserProfile.EMPTY, Set.of(RoleCode.ADMIN), UserStatus.DISABLED)));
        assertEquals(changed, userAccountService.getById(user.id()));
    }

    @Test
    void updateAndStatisticsEnforcePermissionCsrfAndProfileValidation() throws Exception {
        AuthSession admin = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        UserAccount user = userAccountService.createUser(new CreateUserCommand("boundary-user", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        AuthSession researcher = login(user.username().value(), USER_PASSWORD);
        mockMvc.perform(get("/api/v1/users/statistics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/statistics").cookie(researcher.cookie())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/" + user.id()).cookie(admin.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(json(editBody(user, "姓名"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/" + user.id()).cookie(researcher.cookie())
                        .header(researcher.csrfHeader(), researcher.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(editBody(user, "姓名"))))
                .andExpect(status().isForbidden());
        for (Map.Entry<String, Object> invalid : Map.<String, Object>of(
                "realName", "长".repeat(65), "email", "invalid", "phone", "<script>", "roles", java.util.List.of()).entrySet()) {
            var body = new java.util.HashMap<>(editBody(user, "姓名"));
            body.put(invalid.getKey(), invalid.getValue());
            mockMvc.perform(put("/api/v1/users/" + user.id()).cookie(admin.cookie())
                            .header(admin.csrfHeader(), admin.csrfToken()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, userAccountService.getById(user.id()).version());
        assertTrue(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='OPERATION_FAILED'", Long.class) >= 6);
    }

    @Test
    void administratorCannotDisableOrDemoteSelfThroughEitherEntryPoint() throws Exception {
        AuthSession admin = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        UserAccount self = userAccountService.findByUsername(ADMIN_USERNAME).orElseThrow();
        for (String path : java.util.List.of("disable", "roles")) {
            mockMvc.perform(post("/api/v1/users/" + self.id() + "/" + path).cookie(admin.cookie())
                            .header(admin.csrfHeader(), admin.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("version", self.version(), "roles", Set.of("RESEARCHER")))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"));
        }
        var body = new java.util.HashMap<>(editBody(self, "管理员")); body.put("status", "DISABLED");
        mockMvc.perform(put("/api/v1/users/" + self.id()).cookie(admin.cookie())
                        .header(admin.csrfHeader(), admin.csrfToken()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isConflict());
        assertEquals(self, userAccountService.getById(self.id()));
    }

    @Test
    void concurrentAdministratorRemovalAlwaysLeavesOneActiveAdministrator() throws Exception {
        UserAccount first = userAccountService.findByUsername(ADMIN_USERNAME).orElseThrow();
        UserAccount second = userAccountService.createUser(new CreateUserCommand("second-admin", USER_PASSWORD, Set.of(RoleCode.ADMIN)));
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.List.of(first, second).stream().map(user -> executor.submit(() -> {
                ready.countDown(); start.await();
                try { userAccountService.disableUser(user.id(), user.version()); return true; }
                catch (ResourceConflictException expected) { return false; }
            })).toList();
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            int successes = 0;
            for (var future : futures) if (future.get(10, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE status='ACTIVE'", Long.class));
        }
    }

    @Test
    void statisticsDeduplicateRolesAcrossEveryPageAndIncludeDisabledUsers() throws Exception {
        AuthSession admin = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        jdbcTemplate.update("""
                INSERT INTO sys_user(username,password_hash,status) SELECT CONCAT('stats-',n), 'unusable-test-hash', 'DISABLED'
                FROM (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
                UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
                UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
                UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
                UNION ALL SELECT 21) nums
                """);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u CROSS JOIN sys_role r WHERE u.username LIKE 'stats-%' AND r.role_code IN ('RESEARCHER','DATA_OPERATOR')");
        mockMvc.perform(get("/api/v1/users/statistics").cookie(admin.cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(22)).andExpect(jsonPath("$.admin").value(1))
                .andExpect(jsonPath("$.dataOperator").value(21)).andExpect(jsonPath("$.researcher").value(0));
    }

    @Test
    void logsExposeSafeConnectionContextAndFilterOldAndNewEvents() throws Exception {
        CsrfSession csrf = csrfSession();
        mockMvc.perform(post("/api/v1/auth/login").cookie(csrf.cookie()).header(csrf.csrfHeader(), csrf.csrfToken())
                        .header("User-Agent", "Mozilla/5.0 Chrome/130.0").header("X-Forwarded-For", "203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("username", "missing-user", "password", USER_PASSWORD))))
                .andExpect(status().isUnauthorized());
        AuthSession admin = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/v1/operations/audits").cookie(admin.cookie()).param("category", "LOGIN")
                        .param("username", "missing-user").param("result", "FAILURE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].username").value("missing-user"))
                .andExpect(jsonPath("$.items[0].clientIp").value("127.0.0.1"))
                .andExpect(jsonPath("$.items[0].userAgent").value("Mozilla/5.0 Chrome/130.0"));
        mockMvc.perform(get("/api/v1/operations/audits").cookie(admin.cookie()).param("category", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/operations/audits").cookie(admin.cookie())
                        .param("from", "2026-09-06T00:00:00Z").param("to", "2026-09-05T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/operations/audits").cookie(admin.cookie()).param("username", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        String content = mockMvc.perform(get("/api/v1/operations/audits").cookie(admin.cookie()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(content.contains(USER_PASSWORD));
        assertFalse(content.contains(csrf.csrfToken()));
    }

    @Test
    void malformedLoginIsAuditedOnceWithoutReadingRequestBody() throws Exception {
        CsrfSession csrf = csrfSession();
        mockMvc.perform(post("/api/v1/auth/login").cookie(csrf.cookie()).header(csrf.csrfHeader(), csrf.csrfToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='LOGIN_FAILED'", Long.class));
    }

    @Test
    void successAuditFailureRollsBackEditButFailureAuditSurvives() throws Exception {
        AuthSession admin = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        UserAccount user = userAccountService.createUser(new CreateUserCommand("rollback-user", USER_PASSWORD, Set.of(RoleCode.RESEARCHER)));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("模拟成功审计写入失败"))
                .when(auditLogRepository).append(org.mockito.ArgumentMatchers.argThat(
                        record -> record.action() == com.aacv.system.operations.domain.AuditAction.USER_UPDATED));
        mockMvc.perform(put("/api/v1/users/" + user.id()).cookie(admin.cookie())
                        .header(admin.csrfHeader(), admin.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(editBody(user, "不能持久化"))))
                .andExpect(status().isInternalServerError());
        assertEquals(user, userAccountService.getById(user.id()));
        mockMvc.perform(get("/api/v1/operations/audits").cookie(admin.cookie()).param("category", "OPERATION")
                        .param("action", "USER_UPDATED").param("result", "FAILURE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].summary.errorCode").value("INTERNAL_ERROR"));
    }

    @Test
    void failureAuditUsesIndependentTransaction() {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            auditService.recordFailure(null, "USER_UPDATED", "123", 409, "VERSION_CONFLICT",
                    new com.aacv.system.operations.infrastructure.web.AuditRequestMetadata(null, null));
            throw new IllegalStateException("模拟外围事务回滚");
        }));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='OPERATION_FAILED'", Long.class));
    }

    @Test
    void lastAdministratorRoleCannotBeRemovedByApplicationService() {
        UserAccount administrator = userAccountService.findByUsername(ADMIN_USERNAME).orElseThrow();
        assertThrows(ResourceConflictException.class, () -> userAccountService.replaceRoles(
                administrator.id(), administrator.version(), Set.of(RoleCode.RESEARCHER)));
    }

    @Test
    void emptyDatabaseStatisticsReportZeroWithoutInventingRoleMemberships() {
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        assertEquals(new com.aacv.system.identity.domain.UserStatistics(0, 0, 0, 0), userAccountService.statistics());
    }

    private Map<String, Object> editBody(UserAccount user, String name) {
        return Map.of("version", user.version(), "roles", user.roles(), "status", user.status(), "realName", name,
                "email", "profile@example.invalid", "phone", "+86 (010) 1234-5678", "organization", "测试单位",
                "department", "测试院系", "remark", "内部测试资料");
    }

    private AuthSession login(String username, String password) throws Exception {
        CsrfSession csrf = csrfSession();
        MvcResult result = loginAttempt(csrf, username, password)
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotated = result.getResponse().getCookie("SESSION");
        return new AuthSession(
                rotated == null ? csrf.cookie() : rotated,
                csrf.csrfHeader(),
                csrf.csrfToken());
    }

    private CsrfSession csrfSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session);
        return new CsrfSession(session, body.get("headerName").asString(), body.get("token").asString());
    }

    private org.springframework.test.web.servlet.ResultActions loginAttempt(
            CsrfSession csrf, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .cookie(csrf.cookie())
                .header(csrf.csrfHeader(), csrf.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", username, "password", password))));
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private Class<?> updateResult(CountDownLatch ready, CountDownLatch start, Runnable update) throws Exception {
        ready.countDown();
        start.await();
        try {
            update.run();
            return Void.class;
        } catch (VersionConflictException exception) {
            return VersionConflictException.class;
        }
    }

    private record CsrfSession(Cookie cookie, String csrfHeader, String csrfToken) {
    }

    private record AuthSession(Cookie cookie, String csrfHeader, String csrfToken) {
    }
}
