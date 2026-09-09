package com.xsyu.academicgraph.application.auth;

import com.xsyu.academicgraph.api.auth.AuthDtos.LoginRequest;
import com.xsyu.academicgraph.api.auth.AuthDtos.RegisterRequest;
import com.xsyu.academicgraph.api.auth.AuthDtos.SessionUserResponse;
import com.xsyu.academicgraph.api.common.GlobalExceptionHandler.EntityNotFoundException;
import com.xsyu.academicgraph.domain.user.RoleRepository;
import com.xsyu.academicgraph.domain.user.SysRole;
import com.xsyu.academicgraph.domain.user.SysUser;
import com.xsyu.academicgraph.domain.user.SysUserRole;
import com.xsyu.academicgraph.domain.user.UserRepository;
import com.xsyu.academicgraph.domain.user.UserRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证与用户服务：注册 / 登录 / 当前会话查询 / 角色权限汇总。
 * 角色→权限的映射集中在这一个枚举里维护（docs/authorization-matrix.md 的代码化）：
 *   ADMIN   可管理用户、读写全部数据、查看分析
 *   ANALYST 可读写数据、查看分析（不能进后台管理）
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    /**
     * 注册：校验用户名唯一 → BCrypt 加密密码 → 落库 → 默认授予 ANALYST 角色 → 直接建立会话。
     * 整体一个事务：任何一步失败都整体回滚，不会留下"半注册"的用户。
     */
    @Transactional
    public SessionUserResponse register(RegisterRequest request,
                                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("用户名已被占用");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? request.username() : request.displayName());
        user = userRepository.save(user);

        SysRole analyst = roleRepository.findById(SysRole.ID_ANALYST)
                .orElseThrow(() -> new EntityNotFoundException("默认角色 ANALYST 不存在，请检查 sys_role 种子数据"));
        SysUserRole link = new SysUserRole();
        link.setUserId(user.getId());
        link.setRoleId(analyst.getId());
        userRoleRepository.save(link);

        // 注册成功后直接登录：校验账号密码并把认证写进 Session
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        establishSession(httpRequest, httpResponse, auth);
        return buildSessionUser(user.getId());
    }

    /** 登录：AuthenticationManager 校验账号密码，成功后把认证写入会话 */
    @Transactional(readOnly = true)
    public SessionUserResponse login(LoginRequest request,
                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        establishSession(httpRequest, httpResponse, auth);
        SysUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        return buildSessionUser(user.getId());
    }

    /**
     * 把认证信息写进服务端 Session（登录/注册成功后的会话建立）。
     * 注意：Spring Security 6 不会自动把手工设置的 SecurityContext 写回 Session，
     * 必须显式调用 securityContextRepository.saveContext(...)；
     * 保存后再 changeSessionId() 轮换会话 ID，防止"会话固定攻击"（登录前后复用同一 JSESSIONID）。
     */
    private void establishSession(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        request.changeSessionId();
    }

    /** 当前会话：从安全上下文拿登录名（匿名访问时调用方先判空） */
    @Transactional(readOnly = true)
    public SessionUserResponse currentUser(String username) {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        return buildSessionUser(user.getId());
    }

    /** 组装会话响应：用户基本信息 + 角色编码列表 + 展开后的权限列表 */
    private SessionUserResponse buildSessionUser(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        List<String> roleCodes = userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .map(id -> roleRepository.findById(id).map(SysRole::getRoleCode).orElse(null))
                .filter(code -> code != null)
                .toList();
        Set<String> permissions = roleCodes.stream()
                .flatMap(code -> Permissions.PERMISSIONS.getOrDefault(code, Set.of()).stream())
                .collect(Collectors.toSet());
        return new SessionUserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                roleCodes, permissions.stream().sorted().toList());
    }
}
