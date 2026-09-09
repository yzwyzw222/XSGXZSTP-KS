package com.aacv.config;

import com.aacv.domain.user.Role;
import com.aacv.domain.user.User;
import com.aacv.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.Set;

@Component
@Profile("!integration")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User(
                    "admin",
                    passwordEncoder.encode("admin123"),
                    "系统管理员",
                    Set.of(Role.ADMIN)
            );
            userRepository.save(admin);
            log.info("Initialized admin user: admin/admin123");
        }

        if (!userRepository.existsByUsername("operator")) {
            User operator = new User(
                    "operator",
                    passwordEncoder.encode("operator123"),
                    "操作员",
                    Set.of(Role.OPERATOR)
            );
            userRepository.save(operator);
            log.info("Initialized operator user: operator/operator123");
        }
    }
}
