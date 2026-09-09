package com.xsyu.academicgraph.api.admin;

import com.xsyu.academicgraph.api.admin.UserAdminDtos.CreateUserRequest;
import com.xsyu.academicgraph.api.admin.UserAdminDtos.UpdateRolesRequest;
import com.xsyu.academicgraph.api.admin.UserAdminDtos.UpdateStatusRequest;
import com.xsyu.academicgraph.api.admin.UserAdminDtos.UserAdminResponse;
import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.api.common.Paging;
import com.xsyu.academicgraph.application.admin.UserAdminService;
import com.xsyu.academicgraph.application.graph.GraphSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员接口（/api/v1/admin/**）。
 * SecurityConfig 已限定该路径需要 ADMIN 角色，普通用户访问返回 403 FORBIDDEN。
 * 除用户管理外，还提供图同步的手动触发入口（正常由定时任务每 5 秒自动执行）。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;
    private final GraphSyncService graphSyncService;

    /** 用户列表：keyword 按用户名模糊搜索 */
    @GetMapping("/users")
    public PageResponse<UserAdminResponse> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return userAdminService.list(keyword, Paging.of(page, size));
    }

    /** 创建用户：可指定初始角色，返回 201 */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userAdminService.create(request);
    }

    /** 重置用户角色（整体替换） */
    @PutMapping("/users/{id}/roles")
    public UserAdminResponse updateRoles(@PathVariable Long id, @Valid @RequestBody UpdateRolesRequest request) {
        return userAdminService.updateRoles(id, request.roles());
    }

    /** 启用/禁用账号 */
    @PutMapping("/users/{id}/status")
    public UserAdminResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return userAdminService.updateStatus(id, request.status());
    }

    /** 删除账号：禁止删除当前登录账号（服务层校验），返回 204 */
    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id, Authentication authentication) {
        userAdminService.delete(id, authentication.getName());
    }

    /** 手动触发图同步：立即处理全部 PENDING 事件并返回处理条数（排查/演示用） */
    @PostMapping("/sync-graph")
    public Map<String, Integer> syncGraph() {
        return Map.of("processed", graphSyncService.syncNow());
    }
}
