package com.aacv.system.authororcid.infrastructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.DefaultApplicationArguments;

class OrcidQuartzCleanupTests {
    private final Scheduler scheduler = mock(Scheduler.class);
    private final JobKey key = new JobKey("author-orcid", "aacv-author-orcid");

    @Test
    void cleanupFailureIsReportedToStartup() throws Exception {
        var failure = new SchedulerException("test failure");
        when(scheduler.deleteJobs(List.of(key))).thenThrow(failure);
        var exception = assertThrows(IllegalStateException.class,
                () -> new OrcidQuartzCleanup(scheduler).run(new DefaultApplicationArguments()));
        assertSame(failure, exception.getCause());
    }

    @Test
    void legacyJobOnlyRemovesItsOwnSchedule() throws Exception {
        new OrcidQuartzJob().execute(context());
        verify(scheduler).deleteJobs(List.of(key));
        verifyNoMoreInteractions(scheduler);
    }

    @Test
    void legacyJobFailureKeepsTheCauseWithoutImmediateRefire() throws Exception {
        var failure = new SchedulerException("test failure");
        when(scheduler.deleteJobs(List.of(key))).thenThrow(failure);
        var exception = assertThrows(JobExecutionException.class, () -> new OrcidQuartzJob().execute(context()));
        assertSame(failure, exception.getCause());
        assertFalse(exception.refireImmediately());
    }

    private JobExecutionContext context() {
        var context = mock(JobExecutionContext.class);
        when(context.getScheduler()).thenReturn(scheduler);
        when(context.getJobDetail()).thenReturn(JobBuilder.newJob(OrcidQuartzJob.class).withIdentity(key).build());
        return context;
    }
}
