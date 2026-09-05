package com.aacv.system.source.infrastructure.openalex;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aacv.system.source.application.SourceQuotaExhaustedException;
import com.aacv.system.source.domain.SourceConnectionSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAlexQuotaTests {

    private static final Instant NOW = Instant.parse("2026-09-05T01:00:00Z");
    private static final SourceConnectionSettings SETTINGS = new SourceConnectionSettings(
            2, 1, Duration.ofSeconds(5), Duration.ofSeconds(30), 2, 1024 * 1024);

    @Test
    void acceptsValidatedDailyBudgetAndRejectsAmbiguousOrInvalidHeaders() {
        assertEquals(NOW.plusSeconds(65), OpenAlexQuota.resetAt(
                Map.of("X-RateLimit-Remaining", "0.0", "X-RateLimit-Reset", "60"), NOW));
        assertNull(OpenAlexQuota.resetAt(Map.of("X-RateLimit-Remaining", "0"), NOW));
        for (String reset : new String[] {"-1", "86401", "bad", "99999999999999999999999999"}) {
            assertNull(OpenAlexQuota.resetAt(Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset", reset), NOW));
        }
        assertNull(OpenAlexQuota.resetAt(Map.of("X-RateLimit-Remaining", "1", "X-RateLimit-Reset", "60"), NOW));
    }

    @Test
    void sharedGateRejectsNewRequestsUntilBudgetResetAndThenReleasesPermit() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        OpenAlexRequestGate gate = new OpenAlexRequestGate(clock);
        gate.observeQuota(Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Reset", "60"));
        SourceQuotaExhaustedException quota = assertThrows(SourceQuotaExhaustedException.class, () -> gate.acquire(SETTINGS));
        assertEquals(NOW.plusSeconds(65), quota.resumeAt());
        when(clock.instant()).thenReturn(NOW.plusSeconds(65));
        try (var permit = gate.acquire(SETTINGS)) {
            assertNotNull(permit);
        }
    }

    @Test
    void interruptedConcurrencyWaitPreservesInterruptAndReleasesNoUnownedPermit() throws Exception {
        OpenAlexRequestGate gate = new OpenAlexRequestGate(Clock.systemUTC());
        try (var permit = gate.acquire(SETTINGS)) {
            var failure = new java.util.concurrent.CompletableFuture<Boolean>();
            Thread worker = new Thread(() -> {
                try (var unexpected = gate.acquire(SETTINGS)) {
                    failure.complete(false);
                } catch (OpenAlexClientException exception) {
                    failure.complete(Thread.currentThread().isInterrupted() && "INTERRUPTED".equals(exception.category()));
                }
            });
            worker.start();
            try {
                worker.interrupt();
                assertTrue(failure.get(2, java.util.concurrent.TimeUnit.SECONDS));
            } finally {
                worker.interrupt();
                worker.join(2000);
            }
        }
    }
}
