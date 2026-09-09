package com.xsyu.academicgraph.infrastructure.integration;

import com.xsyu.academicgraph.domain.user.SysRole;
import com.xsyu.academicgraph.domain.user.SysUser;
import com.xsyu.academicgraph.domain.user.SysUserRole;
import com.xsyu.academicgraph.domain.user.UserRepository;
import com.xsyu.academicgraph.domain.user.UserRoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 仅为全新整合数据库创建管理员，重启时保留已有账号。 */
@Component
@Profile("integration")
public class IntegrationAdminBootstrap implements CommandLineRunner {
    private final UserRepository users;
    private final UserRoleRepository roles;
    private final PasswordEncoder encoder;
    private final String password;

    public IntegrationAdminBootstrap(UserRepository users, UserRoleRepository roles,
            PasswordEncoder encoder, @Value("${integration.admin-password}") String password) {
        if (password.length() < 16) throw new IllegalArgumentException("整合管理员密码长度不足");
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.existsByUsername("admin")) return;
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setDisplayName("本地整合管理员");
        user.setPasswordHash(encoder.encode(password));
        user = users.save(user);
        SysUserRole role = new SysUserRole();
        role.setUserId(user.getId());
        role.setRoleId(SysRole.ID_ADMIN);
        roles.save(role);
    }
}
