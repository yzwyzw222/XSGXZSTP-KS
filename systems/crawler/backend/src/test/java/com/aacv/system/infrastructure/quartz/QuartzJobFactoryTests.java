package com.aacv.system.infrastructure.quartz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.TriggerBuilder;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class QuartzJobFactoryTests {

    @Test
    void injectsCurrentServicesIntoJobsCreatedByQuartz() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AtomicInteger.class, () -> new AtomicInteger());
            context.refresh();
            var factory = new AutowiringSpringBeanJobFactory();
            factory.setApplicationContext(context);
            var bundle = mock(TriggerFiredBundle.class);
            when(bundle.getJobDetail()).thenReturn(JobBuilder.newJob(ProbeJob.class).build());
            when(bundle.getTrigger()).thenReturn((OperableTrigger) TriggerBuilder.newTrigger().build());
            Job job = (Job) factory.createJobInstance(bundle);
            job.execute(null);
            assertEquals(1, context.getBean(AtomicInteger.class).get());
        }
    }

    public static class ProbeJob implements Job {
        @Autowired
        private AtomicInteger calls;

        @Override
        public void execute(JobExecutionContext context) {
            calls.incrementAndGet();
        }
    }
}
