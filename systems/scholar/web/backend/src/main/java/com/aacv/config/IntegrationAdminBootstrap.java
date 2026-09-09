package com.aacv.config;

import com.aacv.domain.user.Role;
import com.aacv.domain.user.User;
import com.aacv.domain.user.UserRepository;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 整合环境使用外部随机凭据，不启用来源分支的演示账号。 */
@Component
@Profile("integration")
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
