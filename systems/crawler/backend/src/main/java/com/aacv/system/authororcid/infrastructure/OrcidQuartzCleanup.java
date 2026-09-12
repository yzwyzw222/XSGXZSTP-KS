package com.aacv.system.authororcid.infrastructure;

import java.util.List;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class OrcidQuartzCleanup implements ApplicationRunner {
    private final Scheduler scheduler;

    OrcidQuartzCleanup(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            // 批量删除按任务键清理，不读取可能缺失的 SIMPLE 触发器明细。
            scheduler.deleteJobs(List.of(new JobKey("author-orcid", "aacv-author-orcid")));
        } catch (SchedulerException exception) {
            throw new IllegalStateException("无法清理已停用的 ORCID 查询任务", exception);
        }
    }
}
