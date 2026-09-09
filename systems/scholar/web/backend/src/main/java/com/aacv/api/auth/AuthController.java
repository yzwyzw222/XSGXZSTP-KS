package com.aacv.api.auth;

import com.aacv.application.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken, HttpServletResponse response) {
        // 与前端约定：token 通过 X-CSRF-TOKEN 响应头暴露，供内存缓存
        response.setHeader("X-CSRF-TOKEN", csrfToken.getToken());
        return csrfToken;
    }

    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return authService.login(request.getUsername(), request.getPassword(), httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    public void logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        // 过滤器的默认退出入口已关闭，因此必须在业务退出接口销毁会话。
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.getCurrentUser();
    }
}
