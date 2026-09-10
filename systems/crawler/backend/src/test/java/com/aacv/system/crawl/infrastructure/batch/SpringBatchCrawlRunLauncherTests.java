package com.aacv.system.crawl.infrastructure.batch;

import com.aacv.system.crawl.application.CrawlRunService;
import com.aacv.system.crawl.domain.CrawlLaunchFailure;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpringBatchCrawlRunLauncherTests {

    @Test
    void startupFailureHasSafeCategoryAndIsPersisted() throws Exception {
        JobOperator operator = mock(JobOperator.class);
        Job job = mock(Job.class);
        CrawlRunService service = mock(CrawlRunService.class);
        ObjectProvider<CrawlRunService> provider = mock(ObjectProvider.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(provider.getObject()).thenReturn(service);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(operator.start(any(Job.class), any())).thenThrow(new java.util.concurrent.RejectedExecutionException());

        new SpringBatchCrawlRunLauncher(operator, job, provider, manager).launchAfterCommit(7);

        verify(service).failLaunch(7, CrawlLaunchFailure.EXECUTOR_BUSY);
        verify(manager).commit(any());
    }

    @Test
    void rollbackDoesNotLaunchAnUncommittedRun() throws Exception {
        JobOperator operator = mock(JobOperator.class);
        var launcher = new SpringBatchCrawlRunLauncher(operator, mock(Job.class), mock(ObjectProvider.class),
                mock(PlatformTransactionManager.class));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            launcher.launchAfterCommit(7);
            for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verifyNoInteractions(operator);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void classificationDoesNotExposeExceptionMessages() {
        assertEquals(CrawlLaunchFailure.STORAGE_UNAVAILABLE, SpringBatchCrawlRunLauncher.classify(
                new IllegalStateException(new org.springframework.dao.DataAccessResourceFailureException("测试连接错误"))));
        assertEquals(CrawlLaunchFailure.TRANSACTION_FAILED, SpringBatchCrawlRunLauncher.classify(
                new org.springframework.transaction.CannotCreateTransactionException("测试事务错误")));
        assertEquals(CrawlLaunchFailure.INVALID_PARAMETERS, SpringBatchCrawlRunLauncher.classify(new IllegalArgumentException()));
        assertEquals(CrawlLaunchFailure.UNKNOWN, SpringBatchCrawlRunLauncher.classify(new Exception()));
    }
}
