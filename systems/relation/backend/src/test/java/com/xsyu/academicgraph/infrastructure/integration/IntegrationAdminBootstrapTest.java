package com.xsyu.academicgraph.infrastructure.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class IntegrationAdminBootstrapTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=integration")
            .withUserConfiguration(IntegrationAdminBootstrap.class);

    @Test
    void emptyEnvironmentStartsWithoutLocalAccountDependencies() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).doesNotHaveBean(CommandLineRunner.class);
        });
    }

    @Test
    void legacyPasswordDoesNotEnableAccountCreation() {
        context.withPropertyValues("integration.admin-password=" + java.util.UUID.randomUUID())
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application).doesNotHaveBean(CommandLineRunner.class);
                });
    }

    @Test
    void explicitDisableDoesNotRequireAnAdminPassword() {
        context.withPropertyValues("integration.bootstrap-admin.enabled=false")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application).doesNotHaveBean(CommandLineRunner.class);
                });
    }
}
