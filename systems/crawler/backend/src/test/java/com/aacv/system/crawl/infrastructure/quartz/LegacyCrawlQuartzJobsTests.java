package com.aacv.system.crawl.infrastructure.quartz;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

class LegacyCrawlQuartzJobsTests {

    @Test
    void oldJobsOnlyDeleteTheirOwnPersistedSchedule() throws Exception {
        for (Job job : List.of(new QuartzCrawlTriggerJob(), new QuartzQuotaResumeJob())) {
            var scheduler = mock(Scheduler.class);
            var context = mock(JobExecutionContext.class);
            var detail = JobBuilder.newJob(job.getClass()).withIdentity("retired", "old-crawl").build();
            when(context.getScheduler()).thenReturn(scheduler);
            when(context.getJobDetail()).thenReturn(detail);
            job.execute(context);
            verify(scheduler).deleteJob(detail.getKey());
            verifyNoMoreInteractions(scheduler);
        }
    }

    @Test
    void cleanupFailuresRemainVisible() throws Exception {
        for (Job job : List.of(new QuartzCrawlTriggerJob(), new QuartzQuotaResumeJob())) {
            var scheduler = mock(Scheduler.class);
            var context = mock(JobExecutionContext.class);
            var detail = JobBuilder.newJob(job.getClass()).build();
            when(context.getScheduler()).thenReturn(scheduler);
            when(context.getJobDetail()).thenReturn(detail);
            when(scheduler.deleteJob(detail.getKey())).thenThrow(new SchedulerException("调度存储不可用"));
            assertThrows(JobExecutionException.class, () -> job.execute(context));
        }
    }
}
