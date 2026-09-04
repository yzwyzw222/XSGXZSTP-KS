package com.aacv.system.operations.infrastructure.quartz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aacv.system.operations.infrastructure.config.OperationsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.boot.ApplicationArguments;

class AlertEvaluationQuartzScheduleTests {

    @Test
    void registersDurableJobAndRecurringTrigger() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(false);
        OperationsProperties properties = properties(90);

        new AlertEvaluationQuartzSchedule(scheduler, properties).run(mock(ApplicationArguments.class));

        ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).addJob(jobCaptor.capture(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(true));
        verify(scheduler).scheduleJob(triggerCaptor.capture());
        assertEquals("aacv-operations.alert-evaluation", jobCaptor.getValue().getKey().toString());
        assertEquals("aacv-operations.alert-evaluation", triggerCaptor.getValue().getKey().toString());
        assertEquals(90_000L, ((SimpleTrigger) triggerCaptor.getValue()).getRepeatInterval());
    }

    @Test
    void reschedulesExistingTriggerWithoutCreatingDuplicate() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.checkExists(any(TriggerKey.class))).thenReturn(true);

        new AlertEvaluationQuartzSchedule(scheduler, properties(60)).run(mock(ApplicationArguments.class));

        verify(scheduler).rescheduleJob(any(TriggerKey.class), any(Trigger.class));
    }

    private OperationsProperties properties(int intervalSeconds) {
        OperationsProperties properties = new OperationsProperties();
        properties.setAlertEvaluationIntervalSeconds(intervalSeconds);
        return properties;
    }
}
