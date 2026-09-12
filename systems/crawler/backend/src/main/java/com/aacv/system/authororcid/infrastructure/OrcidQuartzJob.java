package com.aacv.system.authororcid.infrastructure;

import java.util.List;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

/** 兼容历史 Quartz 持久化类名，只清理旧任务，不再获取 ORCID。 */
@DisallowConcurrentExecution
public class OrcidQuartzJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            context.getScheduler().deleteJobs(List.of(context.getJobDetail().getKey()));
        } catch (SchedulerException exception) {
            throw new JobExecutionException("无法清理已停用的 ORCID 查询任务", exception, false);
        }
    }
}
