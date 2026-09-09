package com.xsyu.academicgraph.infrastructure.security;

import com.xsyu.academicgraph.domain.user.RoleRepository;
import com.xsyu.academicgraph.domain.user.SysRole;
import com.xsyu.academicgraph.domain.user.SysUser;
import com.xsyu.academicgraph.domain.user.SysUserRole;
import com.xsyu.academicgraph.domain.user.UserRepository;
import com.xsyu.academicgraph.domain.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring Security 的用户加载器：按登录名查 sys_user，再通过 sys_user_role → sys_role 还原角色编码。
 * roles(...) 方法会自动给角色编码加 ROLE_ 前缀，因此配置里的 hasRole("ADMIN") 匹配的就是 ROLE_ADMIN。
 * 账号被禁用（DISABLED）时用 enabled=false 直接拒绝登录。
 */
@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        // 关联表里只存角色 id，这里批量查出角色字典映射成角色编码（如 ADMIN/ANALYST）
        List<SysUserRole> userRoles = userRoleRepository.findByUserId(user.getId());
        Map<Long, SysRole> roleMap = roleRepository.findAllById(
                        userRoles.stream().map(SysUserRole::getRoleId).toList())
                .stream().collect(Collectors.toMap(SysRole::getId, Function.identity()));
        String[] roleCodes = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .map(roleMap::get)
                .filter(role -> role != null)
                .map(SysRole::getRoleCode)
                .toArray(String[]::new);

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(roleCodes)
                .disabled(!SysUser.STATUS_ACTIVE.equals(user.getStatus()))
                .build();
    }
}
