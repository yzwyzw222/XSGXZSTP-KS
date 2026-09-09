package com.aacv.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class OpenApiDocumentTests {

    @Test
    void openApiDocumentIsValidYamlAndContainsStageSevenContracts() throws Exception {
        Path document = Path.of("..", "docs", "openapi.yaml").normalize();
        Map<String, Object> root;
        try (InputStream input = Files.newInputStream(document)) {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        }

        assertEquals("3.1.0", root.get("openapi"));
        Map<?, ?> paths = (Map<?, ?>) root.get("paths");
        assertTrue(paths.containsKey("/api/v1/auth/login"));
        assertTrue(paths.containsKey("/api/v1/users"));
        assertTrue(paths.containsKey("/api/v1/operations/audits"));
        assertTrue(paths.containsKey("/api/v1/sources"));
        assertTrue(paths.containsKey("/api/v1/crawl/tasks"));
        assertTrue(paths.containsKey("/api/v1/crawl/runs/{runId}"));
        assertTrue(paths.containsKey("/api/v1/crawl/runs/{runId}/failures"));
        assertTrue(paths.containsKey("/api/v1/crawl/runs/{runId}/retry-failures"));
        assertTrue(paths.containsKey("/api/v1/catalog/achievements"));
        assertTrue(paths.containsKey("/api/v1/catalog/achievements/{achievementId}"));
        assertTrue(paths.containsKey("/api/v1/catalog/{collection}"));
        assertTrue(paths.containsKey("/api/v1/catalog/{collection}/{entityId}/achievements"));
        assertTrue(paths.containsKey("/api/v1/duplicate-candidates"));
        assertTrue(paths.containsKey("/api/v1/duplicate-candidates/{candidateId}/accept"));
        assertTrue(paths.containsKey("/api/v1/duplicate-candidates/{candidateId}/reject"));
        assertTrue(paths.containsKey("/api/v1/merge-decisions/{decisionId}/revert"));
        assertTrue(paths.containsKey("/api/v1/catalog/achievements/{achievementId}/field-overrides"));
        assertTrue(paths.containsKey("/api/v1/quality-metrics"));
        assertTrue(paths.containsKey("/api/v1/quality-metrics/{metricId}"));
        assertTrue(paths.containsKey("/api/v1/graph/subgraph"));
        assertTrue(paths.containsKey("/api/v1/graph/path"));
        assertTrue(paths.containsKey("/api/v1/graph/sync-status"));
        assertTrue(paths.containsKey("/api/v1/operations/graph-events"));
        assertTrue(paths.containsKey("/api/v1/operations/graph-events/{eventId}/replay"));
        assertTrue(paths.containsKey("/api/v1/operations/graph-maintenance/runs"));
        assertTrue(paths.containsKey("/api/v1/operations/graph-maintenance/backfill"));
        assertTrue(paths.containsKey("/api/v1/operations/graph-maintenance/reconcile"));
        assertTrue(paths.containsKey("/api/v1/operations/graph-maintenance/rebuild"));
        assertTrue(paths.containsKey("/api/v1/analytics/overview"));
        assertTrue(paths.containsKey("/api/v1/analytics/trends"));
        assertTrue(paths.containsKey("/api/v1/analytics/distributions"));
        assertTrue(paths.containsKey("/api/v1/analytics/collaboration"));
        assertTrue(paths.containsKey("/api/v1/exports"));
        assertTrue(paths.containsKey("/api/v1/exports/{exportId}"));
        assertTrue(paths.containsKey("/api/v1/exports/{exportId}/download"));
        assertTrue(paths.containsKey("/api/v1/operations/overview"));
        assertTrue(paths.containsKey("/api/v1/operations/alerts"));
        assertTrue(paths.containsKey("/api/v1/operations/alerts/{alertId}/acknowledge"));
        assertFalse(paths.keySet().stream().map(Object::toString).anyMatch(path ->
                path.contains("raw-record") || path.contains("payload")));
        assertFalse(paths.keySet().stream().map(Object::toString).anyMatch(path ->
                path.contains("resolution")));

        Map<?, ?> analyticsOverview = (Map<?, ?>) paths.get("/api/v1/analytics/overview");
        Map<?, ?> analyticsGet = (Map<?, ?>) analyticsOverview.get("get");
        assertEquals("ANALYTICS_READ", analyticsGet.get("x-required-permission"));
        Map<?, ?> exportPath = (Map<?, ?>) paths.get("/api/v1/exports");
        Map<?, ?> exportPost = (Map<?, ?>) exportPath.get("post");
        assertEquals("EXPORT_CREATE", exportPost.get("x-required-permission"));
        Map<?, ?> alertAcknowledge = (Map<?, ?>) paths.get("/api/v1/operations/alerts/{alertId}/acknowledge");
        Map<?, ?> alertPost = (Map<?, ?>) alertAcknowledge.get("post");
        assertEquals("ALERT_MANAGE", alertPost.get("x-required-permission"));

        Map<?, ?> components = (Map<?, ?>) root.get("components");
        Map<?, ?> schemas = (Map<?, ?>) components.get("schemas");
        Map<?, ?> permission = (Map<?, ?>) schemas.get("Permission");
        List<?> permissions = (List<?>) permission.get("enum");
        assertTrue(permissions.containsAll(List.of(
                "ANALYTICS_READ", "EXPORT_CREATE", "EXPORT_READ", "OPERATIONS_READ", "ALERT_MANAGE")));
    }
}
