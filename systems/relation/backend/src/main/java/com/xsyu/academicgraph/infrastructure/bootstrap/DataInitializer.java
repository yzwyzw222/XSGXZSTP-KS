package com.xsyu.academicgraph.infrastructure.bootstrap;

import com.xsyu.academicgraph.domain.user.SysRole;
import com.xsyu.academicgraph.domain.user.SysUser;
import com.xsyu.academicgraph.domain.user.SysUserRole;
import com.xsyu.academicgraph.domain.user.UserRepository;
import com.xsyu.academicgraph.domain.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动引导数据：首次启动时若不存在 admin 账号则自动创建（密码 admin123，首次登录后请修改）。
 * 这是课程设计演示用的种子账号；真实系统应由运维初始化并在交付前强制改密。
 * CommandLineRunner 在应用启动完成后执行，包在事务里保证"要么建全、要么不建"。
 */
@Component
@Profile("!integration")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByUsername("admin")) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setDisplayName("系统管理员");
        admin = userRepository.save(admin);

        SysUserRole link = new SysUserRole();
        link.setUserId(admin.getId());
        link.setRoleId(SysRole.ID_ADMIN);
        userRoleRepository.save(link);
    }
}
