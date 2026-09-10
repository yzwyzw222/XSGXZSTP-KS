package com.aacv.system.crawl.infrastructure.batch;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aacv.system.crawl.application.CrawlRunService;
import com.aacv.system.source.application.SourceClientException;
import com.aacv.system.source.application.SourceQuotaExhaustedException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

class CrawlJobExecutionListenerTests {
    @Test
    void recordsSafeFailureStageWithoutCopyingOriginalExceptionMessage() {
        assertFailure(new SourceClientException("HTTP_400", false, 400, "response-body-must-not-leak"), "FETCH");
        assertFailure(new SourceClientException("PARSE_FAILURE", false, 200, "response-body-must-not-leak"), "PARSE");
        assertFailure(new DataIntegrityViolationException("response-body-must-not-leak"), "PERSIST");
    }

    @Test
    void quotaDeferralDoesNotCreateExecutionFailure() {
        CrawlRunService service = mock(CrawlRunService.class);
        Instant resume = Instant.parse("2026-09-11T00:00:00Z");
        listener(service).afterJob(execution(new SourceQuotaExhaustedException(resume)));
        verify(service).completeBatch(7, false, resume, null);
    }

    private void assertFailure(Throwable error, String stage) {
        CrawlRunService service = mock(CrawlRunService.class);
        listener(service).afterJob(execution(new RuntimeException(error)));
        verify(service).completeBatch(eq(7L), eq(false), isNull(), argThat(failure ->
                failure.stage().equals(stage) && !failure.message().contains("response-body-must-not-leak")));
    }

    @SuppressWarnings("unchecked")
    private CrawlJobExecutionListener listener(CrawlRunService service) {
        ObjectProvider<CrawlRunService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(service);
        return new CrawlJobExecutionListener(provider);
    }

    private JobExecution execution(Throwable failure) {
        JobExecution execution = mock(JobExecution.class);
        when(execution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(execution.getJobParameters()).thenReturn(new JobParametersBuilder().addLong("runId", 7L).toJobParameters());
        when(execution.getAllFailureExceptions()).thenReturn(List.of(failure));
        return execution;
    }
}
