package com.aacv.system.operations.infrastructure.quartz;

import com.aacv.system.operations.application.AlertEvaluationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class AlertEvaluationQuartzJob implements Job {

    @Autowired
    private AlertEvaluationService alertEvaluationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            alertEvaluationService.evaluate();
        } catch (RuntimeException exception) {
            throw new JobExecutionException("系统内告警评估失败", exception, false);
        }
    }
}
