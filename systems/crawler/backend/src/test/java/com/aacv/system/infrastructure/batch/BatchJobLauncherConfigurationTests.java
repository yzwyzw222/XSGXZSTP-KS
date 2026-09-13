package com.aacv.system.infrastructure.batch;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class BatchJobLauncherConfigurationTests {

    @Test
    void suppliesTheGraphMaintenanceOperatorAndExecutesBackgroundWork() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JobRepository.class, () -> mock(JobRepository.class));
            context.register(BatchJobLauncherConfiguration.class);
            context.refresh();
            assertNotNull(context.getBean("batchJobOperator", JobOperator.class));
            var completed = new CountDownLatch(1);
            context.getBean("batchJobTaskExecutor", ThreadPoolTaskExecutor.class).execute(completed::countDown);
            assertTrue(completed.await(5, TimeUnit.SECONDS));
        }
    }
}
