package com.xsyu.academicgraph.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 认证模块的请求/响应 DTO。
 * 用 record 定义不可变数据载体，配合 Bean Validation 注解在进服务层之前完成参数校验。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 注册请求：用户名只允许字母数字下划线，密码至少 6 位 */
    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 64, message = "用户名长度须在 3~64 之间")
            @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
            String username,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 64, message = "密码长度须在 6~64 之间")
            String password,

            @Size(max = 64, message = "显示名称最长 64 字符")
            String displayName
    ) {
    }

    /** 登录请求 */
    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password
    ) {
    }

    /** 会话用户信息：前端存内存会话 + 权限判断（hasRole/hasPermission）都用它 */
    public record SessionUserResponse(
            Long id,
            String username,
            String displayName,
            List<String> roles,
            List<String> permissions
    ) {
    }
}
