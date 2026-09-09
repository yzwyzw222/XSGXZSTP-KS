package com.aacv.config;

import com.aacv.domain.user.Role;
import com.aacv.domain.user.User;
import com.aacv.domain.user.UserRepository;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 保留显式启用的旧引导入口；统一登录模式默认不创建子系统账号。 */
@Component
@Profile("integration")
@ConditionalOnProperty(name = "integration.bootstrap-admin.enabled", havingValue = "true")
public class IntegrationAdminBootstrap implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String password;

    public IntegrationAdminBootstrap(UserRepository users, PasswordEncoder encoder,
            @Value("${integration.admin-password}") String password) {
        if (password.length() < 16) throw new IllegalArgumentException("整合管理员密码长度不足");
        this.users = users;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!users.existsByUsername("admin")) {
            users.save(new User("admin", encoder.encode(password), "本地整合管理员", Set.of(Role.ADMIN)));
        }
    }
}
