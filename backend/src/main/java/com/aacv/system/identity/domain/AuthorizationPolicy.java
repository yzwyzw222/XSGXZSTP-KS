package com.aacv.system.identity.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class AuthorizationPolicy {

    private static final Map<RoleCode, Set<Permission>> ROLE_PERMISSIONS = buildRolePermissions();

    private AuthorizationPolicy() {
    }

    public static boolean isAllowed(RoleCode role, Permission permission) {
        if (role == null || permission == null) {
            return false;
        }
        return ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(permission);
    }

    public static Set<Permission> permissionsFor(Set<RoleCode> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }

        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        roles.stream()
                .filter(role -> role != null)
                .map(role -> ROLE_PERMISSIONS.getOrDefault(role, Set.of()))
                .forEach(permissions::addAll);
        return Set.copyOf(permissions);
    }

    private static Map<RoleCode, Set<Permission>> buildRolePermissions() {
        EnumMap<RoleCode, Set<Permission>> permissions = new EnumMap<>(RoleCode.class);
        permissions.put(RoleCode.ADMIN, Set.copyOf(EnumSet.allOf(Permission.class)));
        permissions.put(RoleCode.DATA_OPERATOR, Set.of(
                Permission.ACCOUNT_SELF_READ,
                Permission.SOURCE_READ,
                Permission.CRAWL_TASK_READ,
                Permission.CRAWL_TASK_CREATE,
                Permission.CRAWL_TASK_UPDATE,
                Permission.CRAWL_TASK_CONTROL,
                Permission.CRAWL_SCHEDULE_MANAGE,
                Permission.CRAWL_RUN_READ,
                Permission.GOVERNANCE_READ,
                Permission.GOVERNANCE_MANAGE,
                Permission.CATALOG_READ,
                Permission.GRAPH_READ,
                Permission.GRAPH_SYNC_READ,
                Permission.GRAPH_SYNC_MANAGE,
                Permission.ANALYTICS_READ,
                Permission.EXPORT_CREATE,
                Permission.EXPORT_READ));
        permissions.put(RoleCode.RESEARCHER, Set.of(
                Permission.ACCOUNT_SELF_READ,
                Permission.CATALOG_READ,
                Permission.GRAPH_READ,
                Permission.ANALYTICS_READ,
                Permission.EXPORT_CREATE,
                Permission.EXPORT_READ));
        return Map.copyOf(permissions);
    }
}
