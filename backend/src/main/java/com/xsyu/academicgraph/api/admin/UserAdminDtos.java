package com.xsyu.academicgraph.api.admin;

import com.xsyu.academicgraph.domain.user.SysUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员接口（/api/v1/admin/**）的请求/响应 DTO。
 * 整个路径被 SecurityConfig 限定为 ADMIN 角色专属，普通用户访问返回 403。
 */
public final class UserAdminDtos {

    private UserAdminDtos() {
    }

    /** 管理员创建用户：可指定初始角色（ADMIN/ANALYST），不填默认 ANALYST */
    public record CreateUserRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 64, message = "用户名长度须在 3~64 之间")
            @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
            String username,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度须在 6~64 之间")
            String password,

            @Size(max = 64, message = "显示名称最长 64 字符")
            String displayName,

            List<String> roles
    ) {
    }

    /** 重置用户角色：整体替换（不是追加） */
    public record UpdateRolesRequest(
            @NotNull(message = "roles 不能为空")
            List<String> roles
    ) {
    }

    /** 修改账号状态：ACTIVE=正常 / DISABLED=禁用（禁用后无法登录） */
    public record UpdateStatusRequest(
            @NotBlank(message = "status 不能为空")
            String status
    ) {
    }

    /** 用户管理列表项 */
    public record UserAdminResponse(
            Long id,
            String username,
            String displayName,
            String status,
            List<String> roles,
            LocalDateTime createdAt
    ) {
        public static UserAdminResponse from(SysUser user, List<String> roles) {
            return new UserAdminResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                    user.getStatus(), roles, user.getCreatedAt());
        }
    }
}
