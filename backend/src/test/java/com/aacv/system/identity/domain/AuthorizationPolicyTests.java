package com.aacv.system.identity.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizationPolicyTests {

    @Test
    void administratorHasAllPermissions() {
        for (Permission permission : Permission.values()) {
            assertTrue(AuthorizationPolicy.isAllowed(RoleCode.ADMIN, permission));
        }
    }

    @Test
    void operatorCanControlCrawlsButCannotManageSourcesOrUsers() {
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.CRAWL_TASK_CREATE));
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.CRAWL_TASK_CONTROL));
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.CATALOG_READ));
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.GOVERNANCE_READ));
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.GOVERNANCE_MANAGE));
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.GRAPH_READ));
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.GRAPH_SYNC_READ));
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.GRAPH_SYNC_MANAGE));
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.ANALYTICS_READ));
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.EXPORT_CREATE));
        assertTrue(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.EXPORT_READ));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.SOURCE_MANAGE));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.SOURCE_PROBE));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.USER_CREATE));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.AUDIT_READ));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.OPERATIONS_READ));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.DATA_OPERATOR, Permission.ALERT_MANAGE));
    }

    @Test
    void researcherCanReadBusinessDataAndManageOwnExportsOnly() {
        assertEquals(
                Set.of(
                        Permission.ACCOUNT_SELF_READ,
                        Permission.CATALOG_READ,
                        Permission.GRAPH_READ,
                        Permission.ANALYTICS_READ,
                        Permission.EXPORT_CREATE,
                        Permission.EXPORT_READ),
                AuthorizationPolicy.permissionsFor(Set.of(RoleCode.RESEARCHER)));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.RESEARCHER, Permission.CRAWL_TASK_READ));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.RESEARCHER, Permission.SOURCE_READ));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.RESEARCHER, Permission.GRAPH_SYNC_READ));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.RESEARCHER, Permission.GRAPH_SYNC_MANAGE));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.RESEARCHER, Permission.OPERATIONS_READ));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.RESEARCHER, Permission.ALERT_MANAGE));
    }

    @Test
    void nullAndEmptyInputsDoNotGrantPermissions() {
        assertFalse(AuthorizationPolicy.isAllowed(null, Permission.USER_LIST));
        assertFalse(AuthorizationPolicy.isAllowed(RoleCode.ADMIN, null));
        assertTrue(AuthorizationPolicy.permissionsFor(Set.of()).isEmpty());
        assertTrue(AuthorizationPolicy.permissionsFor(null).isEmpty());
    }
}
