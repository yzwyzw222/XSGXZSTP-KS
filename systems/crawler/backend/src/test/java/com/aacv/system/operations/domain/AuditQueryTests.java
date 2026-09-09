package com.aacv.system.operations.domain;

import static org.junit.jupiter.api.Assertions.*;
import com.aacv.system.operations.infrastructure.web.AuditRequestMetadata;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuditQueryTests {
    @Test
    void validatesRangeCategoryAndLiteralSearch() {
        assertThrows(IllegalArgumentException.class, () -> new AuditQuery(AuditCategory.LOGIN, null, null, null, null, AuditAction.USER_UPDATED));
        assertThrows(IllegalArgumentException.class, () -> new AuditQuery(null, null, Instant.EPOCH, Instant.EPOCH, null, null));
        assertEquals("!%!_!!", new AuditQuery(null, "%_!", null, null, null, null).escapedUsername());
        assertEquals(AuditCategory.LOGIN, AuditCategory.of(AuditAction.LOGOUT));
        assertEquals(AuditCategory.OPERATION, AuditCategory.of(AuditAction.OPERATION_FAILED));
    }

    @Test
    void boundsClientMetadataAndIgnoresForwardedAddress() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.99");
        request.addHeader("User-Agent", "A\r\nB" + "x".repeat(1000));
        var metadata = AuditRequestMetadata.from(request);
        assertEquals("127.0.0.1", metadata.clientIp());
        assertEquals(512, metadata.userAgent().length());
        assertFalse(metadata.userAgent().contains("\n"));
        assertTrue(metadata.userAgent().startsWith("AB"));
    }
}
