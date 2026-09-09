package com.aacv.system.identity.infrastructure.config;

import com.aacv.system.identity.application.CreateUserCommand;
import com.aacv.system.identity.application.UserAccountService;
import com.aacv.system.identity.domain.RoleCode;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class InitialAdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAdminBootstrap.class);

    private final InitialAdminProperties properties;
    private final UserAccountService userAccountService;

    public InitialAdminBootstrap(InitialAdminProperties properties, UserAccountService userAccountService) {
        this.properties = properties;
        this.userAccountService = userAccountService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.getUsername() == null || properties.getPassword() == null) {
            throw new IllegalStateException("启用初始管理员引导时必须提供用户名和密码环境变量");
        }

        userAccountService.bootstrapInitialAdministrator(new CreateUserCommand(
                        properties.getUsername(), properties.getPassword(), Set.of(RoleCode.ADMIN)))
                .ifPresentOrElse(
                        account -> LOGGER.info("初始管理员引导完成，userId={}", account.id()),
                        () -> LOGGER.info("用户表非空，已跳过初始管理员引导"));
    }
}
