package com.aacv.system.operations.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditRecordTests {

    @Test
    void acceptsSmallNonSensitiveSummary() {
        AuditRecord record = new AuditRecord(
                1L,
                AuditAction.USER_DISABLED,
                "USER_ACCOUNT",
                "12",
                AuditResult.SUCCESS,
                "0123456789abcdef",
                Map.of("status", "DISABLED"),
                Instant.parse("2026-09-01T12:00:00Z"));

        assertEquals("DISABLED", record.summary().get("status"));
    }

    @Test
    void rejectsSensitiveSummaryKeys() {
        for (String key : new String[] {"password", "sessionId", "cookieValue", "apiToken", "clientSecret"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new AuditRecord(
                            null,
                            AuditAction.LOGIN_FAILED,
                            "USER_ACCOUNT",
                            "unknown",
                            AuditResult.FAILURE,
                            "0123456789abcdef",
                            Map.of(key, "redacted"),
                            Instant.parse("2026-09-01T12:00:00Z")));
        }
    }

    @Test
    void summaryIsImmutable() {
        AuditRecord record = new AuditRecord(
                null,
                AuditAction.LOGIN_FAILED,
                "USER_ACCOUNT",
                "unknown",
                AuditResult.FAILURE,
                "0123456789abcdef",
                Map.of(),
                Instant.parse("2026-09-01T12:00:00Z"));

        assertTrue(record.summary().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> record.summary().put("reason", "changed"));
    }
}
