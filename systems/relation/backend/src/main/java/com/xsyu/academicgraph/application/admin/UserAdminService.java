package com.xsyu.academicgraph.application.admin;

import com.xsyu.academicgraph.api.admin.UserAdminDtos.CreateUserRequest;
import com.xsyu.academicgraph.api.admin.UserAdminDtos.UserAdminResponse;
import com.xsyu.academicgraph.api.common.GlobalExceptionHandler.EntityNotFoundException;
import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.domain.user.SysRole;
import com.xsyu.academicgraph.domain.user.SysUser;
import com.xsyu.academicgraph.domain.user.SysUserRole;
import com.xsyu.academicgraph.domain.user.RoleRepository;
import com.xsyu.academicgraph.domain.user.UserRepository;
import com.xsyu.academicgraph.domain.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员用户管理服务（角色 ADMIN 专属，Controller 路径已被安全配置保护）。
 * 与注册流程的差别：管理员可以指定初始角色、重置角色、禁用账号、删除账号。
 * 密码与注册一致只存 BCrypt 哈希；角色变更不产生图同步事件（用户不投影进 Neo4j）。
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    /** 用户列表：keyword 为空查全部，否则按用户名模糊搜索；角色批量组装避免 N+1 */
    @Transactional(readOnly = true)
    public PageResponse<UserAdminResponse> list(String keyword, Pageable pageable) {
        Page<SysUser> page = (keyword == null || keyword.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingIgnoreCase(keyword.trim(), pageable);
        List<SysUser> users = page.getContent();

        // 一次 IN 查询拉出整页用户的角色行 + 角色字典，内存里做关联
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        List<SysUserRole> roleRows = userIds.isEmpty() ? List.of()
                : userRoleRepository.findByUserIdIn(userIds);
        Map<Long, SysRole> rolesById = roleRepository.findAllById(
                        roleRows.stream().map(SysUserRole::getRoleId).distinct().toList())
                .stream().collect(Collectors.toMap(SysRole::getId, Function.identity()));
        Map<Long, List<String>> rolesByUser = roleRows.stream()
                .collect(Collectors.groupingBy(SysUserRole::getUserId,
                        Collectors.mapping(ur -> rolesById.get(ur.getRoleId()).getRoleCode(), Collectors.toList())));

        List<UserAdminResponse> items = users.stream()
                .map(u -> UserAdminResponse.from(u, rolesByUser.getOrDefault(u.getId(), List.of())))
                .toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** 创建用户：用户名唯一预检 → BCrypt 加密 → 建号 → 授权（默认 ANALYST） */
    @Transactional
    public UserAdminResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        List<SysRole> roles = resolveRoles(request.roles());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password())); // 只存 BCrypt 哈希，绝不存明文
        user.setDisplayName(blankToNull(request.displayName()));
        user.setStatus(SysUser.STATUS_ACTIVE);
        user = userRepository.save(user);

        for (SysRole role : roles) {
            assignRole(user.getId(), role.getId());
        }
        return UserAdminResponse.from(user, roles.stream().map(SysRole::getRoleCode).toList());
    }

    /** 重置角色：整体替换（先删后建），幂等且简单 */
    @Transactional
    public UserAdminResponse updateRoles(Long id, List<String> roleCodes) {
        SysUser user = mustFind(id);
        List<SysRole> roles = resolveRoles(roleCodes);
        userRoleRepository.deleteByUserId(id);
        for (SysRole role : roles) {
            assignRole(id, role.getId());
        }
        return UserAdminResponse.from(user, roles.stream().map(SysRole::getRoleCode).toList());
    }

    /** 启用/禁用账号：DISABLED 账号保留数据但无法登录（登录时抛 401） */
    @Transactional
    public UserAdminResponse updateStatus(Long id, String status) {
        if (!SysUser.STATUS_ACTIVE.equals(status) && !SysUser.STATUS_DISABLED.equals(status)) {
            throw new IllegalArgumentException("status 只能是 ACTIVE 或 DISABLED");
        }
        SysUser user = mustFind(id);
        user.setStatus(status);
        userRepository.save(user);
        return UserAdminResponse.from(user, roleCodesOf(id));
    }

    /** 删除账号：连带删除用户-角色关联（外键级联）；禁止删除自己，防止管理员把自己锁在系统外 */
    @Transactional
    public void delete(Long id, String currentUsername) {
        SysUser user = mustFind(id);
        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalArgumentException("不能删除当前登录的账号");
        }
        userRepository.delete(user);
    }

    /** 角色码 → 角色字典：码不存在时报错；请求未给角色时默认 ANALYST */
    private List<SysRole> resolveRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of(roleRepository.findById(SysRole.ID_ANALYST).orElseThrow());
        }
        List<String> normalized = roleCodes.stream().map(String::trim)
                .filter(code -> !code.isEmpty()).distinct().toList();
        Map<String, SysRole> byCode = roleRepository.findByRoleCodeIn(normalized).stream()
                .collect(Collectors.toMap(SysRole::getRoleCode, Function.identity()));
        for (String code : normalized) {
            if (!byCode.containsKey(code)) {
                throw new IllegalArgumentException("角色不存在: " + code);
            }
        }
        return normalized.stream().map(byCode::get).toList();
    }

    private void assignRole(Long userId, Long roleId) {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRoleRepository.save(userRole);
    }

    private List<String> roleCodesOf(Long userId) {
        List<Long> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRole::getRoleId).toList();
        return roleRepository.findAllById(roleIds).stream()
                .map(SysRole::getRoleCode).toList();
    }

    private SysUser mustFind(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: id=" + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
