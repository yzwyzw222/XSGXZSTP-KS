package com.xsyu.academicgraph.application.auth;

import java.util.Map;
import java.util.Set;

/**
 * 角色→权限映射表（与 docs/authorization-matrix.md 保持一致）。
 * 权限编码采用 "资源:动作" 命名：如 data:write 表示学术数据的写权限。
 * 前端 session.ts 的 hasPermission 判断用的就是这份数据（登录时随会话返回）。
 */
public final class Permissions {

    public static final String USER_MANAGE = "user:manage";
    public static final String DATA_READ = "data:read";
    public static final String DATA_WRITE = "data:write";
    public static final String ANALYTICS_READ = "analytics:read";
    public static final String SYNC_MANAGE = "sync:manage";

    public static final Map<String, Set<String>> PERMISSIONS = Map.of(
            "ADMIN", Set.of(USER_MANAGE, DATA_READ, DATA_WRITE, ANALYTICS_READ, SYNC_MANAGE),
            "ANALYST", Set.of(DATA_READ, DATA_WRITE, ANALYTICS_READ)
    );

    private Permissions() {
    }
}
