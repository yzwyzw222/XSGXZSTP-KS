package com.aacv.system.source.infrastructure.openalex;

import com.aacv.system.source.application.SourceQuotaExhaustedException;
import com.aacv.system.source.domain.SourceConnectionSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

@Component
class OpenAlexRequestGate {

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition available = lock.newCondition();
    private int inFlight;
    private long nextRequestAtNanos;
    private final Clock clock;
    private Instant quotaResetAt;

    OpenAlexRequestGate(Clock clock) {
        this.clock = clock;
    }

    void observeQuota(Map<String, String> metadata) {
        Instant reset = OpenAlexQuota.resetAt(metadata, clock.instant());
        if (reset == null) return;
        lock.lock();
        try {
            if (quotaResetAt == null || reset.isAfter(quotaResetAt)) quotaResetAt = reset;
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }

    Permit acquire(SourceConnectionSettings settings) {
        lock.lock();
        try {
            while (true) {
                if (quotaResetAt != null && clock.instant().isBefore(quotaResetAt)) {
                    throw new SourceQuotaExhaustedException(quotaResetAt);
                }
                long now = System.nanoTime();
                long intervalNanos = Duration.ofSeconds(1).toNanos() / settings.requestsPerSecond();
                if (inFlight < settings.maxConcurrency() && now >= nextRequestAtNanos) {
                    inFlight++;
                    nextRequestAtNanos = now + intervalNanos;
                    return new Permit(this);
                }
                long waitNanos = Math.max(1, nextRequestAtNanos - now);
                try {
                    if (inFlight >= settings.maxConcurrency()) available.await();
                    else available.awaitNanos(waitNanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new OpenAlexClientException(
                            "INTERRUPTED", false, null, "等待OpenAlex请求额度时被中断", exception);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void release() {
        lock.lock();
        try {
            inFlight--;
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }

    static final class Permit implements AutoCloseable {

        private final OpenAlexRequestGate gate;
        private boolean closed;

        private Permit(OpenAlexRequestGate gate) {
            this.gate = gate;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                gate.release();
            }
        }
    }
}
