package com.xsyu.academicgraph.api.auth;

import com.xsyu.academicgraph.api.auth.AuthDtos.LoginRequest;
import com.xsyu.academicgraph.api.auth.AuthDtos.RegisterRequest;
import com.xsyu.academicgraph.api.auth.AuthDtos.SessionUserResponse;
import com.xsyu.academicgraph.application.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口（/api/v1/auth）。
 * 会话机制说明：登录成功后 Spring Security 把认证信息放进服务端 Session，
 * 浏览器凭 Set-Cookie 里的 JSESSIONID 自动携带会话，后续请求无需再传 Token。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 获取 CSRF Token：前端所有写请求（POST/PUT/DELETE）都要带 X-CSRF-TOKEN 头 */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    /** 注册：默认授予 ANALYST 角色，成功后直接建立会话 */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionUserResponse register(@Valid @RequestBody RegisterRequest request,
                                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return authService.register(request, httpRequest, httpResponse);
    }

    /** 登录：成功后建立会话，返回用户信息与权限 */
    @PostMapping("/login")
    public SessionUserResponse login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return authService.login(request, httpRequest, httpResponse);
    }

    /** 当前登录用户信息：前端刷新页面后用这个接口恢复会话状态 */
    @GetMapping("/me")
    public SessionUserResponse me(Authentication authentication) {
        if (authentication.getDetails() instanceof SessionUserResponse portalUser) return portalUser;
        return authService.currentUser(authentication.getName());
    }

    /** 退出登录：销毁服务端会话，浏览器端的 JSESSIONID 随之失效 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false); // false：没有会话不新建
        if (session != null) {
            session.invalidate();
        }
    }
}
