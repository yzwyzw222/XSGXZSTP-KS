package com.aacv.system.crawl.infrastructure.quartz;

import com.aacv.system.crawl.application.CrawlRunService;
import com.aacv.system.crawl.application.port.CrawlRepository;
import java.time.Clock;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class QuartzQuotaResumeJob implements Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzQuotaResumeJob.class);
    @Autowired private CrawlRepository repository;
    @Autowired private CrawlRunService runService;
    @Autowired private Clock clock;

    @Override
    public void execute(JobExecutionContext context) {
        for (long runId : repository.findDueQuotaRuns(clock.instant(), 20)) {
            try {
                runService.resumeQuotaIfDue(runId);
            } catch (RuntimeException exception) {
                // 单个运行恢复失败不阻断其余运行；保留数据库状态供下次扫描和运维排查。
                LOGGER.error("额度恢复失败，runId={}，异常类型={}", runId, exception.getClass().getSimpleName());
            }
        }
    }
}
