package com.aacv.system.operations.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aacv.system.operations.application.AuditService;
import com.aacv.system.shared.infrastructure.web.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(OutputCaptureExtension.class)
class FailedOperationAuditFilterTests {
    @AfterEach
    void clearContext() {
        TraceContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void oversizedInvalidTargetStillRecordsTheOperationFailure() throws Exception {
        AuditService service = mock(AuditService.class);
        var request = new MockHttpServletRequest("PUT", "/api/v1/users/" + "1".repeat(129));
        var response = new MockHttpServletResponse();
        new FailedOperationAuditFilter(service).doFilter(request, response, (incoming, outgoing) -> response.setStatus(400));
        verify(service).recordFailure(isNull(), eq("USER_UPDATED"), isNull(), eq(400), eq("HTTP_400"), any());
    }

    @Test
    void unavailableFailureAuditPreservesResponseAndLogsOnlySafeContext(CapturedOutput output) throws Exception {
        AuditService service = mock(AuditService.class);
        doThrow(new DataAccessResourceFailureException("unsafe-exception-body"))
                .when(service).recordFailure(isNull(), eq("USER_UPDATED"), eq("2"), eq(409), eq("VERSION_CONFLICT"), any());
        TraceContext.put("failure-audit-test");
        var request = new MockHttpServletRequest("PUT", "/api/v1/users/2");
        request.setAttribute(FailedOperationAuditFilter.ERROR_CODE, "VERSION_CONFLICT");
        var response = new MockHttpServletResponse();
        new FailedOperationAuditFilter(service).doFilter(request, response, (incoming, outgoing) -> response.setStatus(409));
        assertEquals(409, response.getStatus());
        assertTrue(output.getAll().contains("failure-audit-test"));
        assertTrue(output.getAll().contains("DataAccessResourceFailureException"));
        assertFalse(output.getAll().contains("unsafe-exception-body"));
    }
}
