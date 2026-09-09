package com.aacv.system.source.infrastructure.crossref;

import com.aacv.system.source.domain.SourceConnectionSettings;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

@Component
class CrossrefRequestGate {

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition available = lock.newCondition();
    private int inFlight;
    private int serverConcurrencyLimit = 1;
    private Duration serverInterval = Duration.ofSeconds(1);
    private int serverRequestLimit = 1;
    private long nextRequestAtNanos;

    Permit acquire(SourceConnectionSettings settings) {
        lock.lock();
        try {
            while (true) {
                long now = System.nanoTime();
                int concurrencyLimit = Math.min(settings.maxConcurrency(), serverConcurrencyLimit);
                int requestsPerInterval = Math.min(settings.requestsPerSecond(), serverRequestLimit);
                long intervalNanos = Math.max(
                        1, serverInterval.toNanos() / Math.max(1, requestsPerInterval));
                if (inFlight < concurrencyLimit && now >= nextRequestAtNanos) {
                    inFlight++;
                    nextRequestAtNanos = now + intervalNanos;
                    return new Permit(this);
                }
                long waitNanos = Math.max(1, nextRequestAtNanos - now);
                try {
                    available.awaitNanos(waitNanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new CrossrefClientException(
                            "INTERRUPTED", false, null, "等待Crossref请求额度时被中断", exception);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    void updatePolicy(Map<String, String> metadata) {
        lock.lock();
        try {
            serverConcurrencyLimit = boundedPositive(
                    metadata.get("X-Concurrency-Limit"), serverConcurrencyLimit, 1, 4);
            serverRequestLimit = boundedPositive(
                    metadata.get("X-Rate-Limit-Limit"), serverRequestLimit, 1, 100);
            serverInterval = parseInterval(metadata.get("X-Rate-Limit-Interval"), serverInterval);
            available.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private int boundedPositive(String value, int fallback, int minimum, int maximum) {
        if (value == null) {
            return fallback;
        }
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(value.trim())));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private Duration parseInterval(String value, Duration fallback) {
        if (value == null || !value.matches("\\d+(?:\\.\\d+)?s")) {
            return fallback;
        }
        try {
            long millis = Math.round(Double.parseDouble(value.substring(0, value.length() - 1)) * 1_000);
            return Duration.ofMillis(Math.max(1, Math.min(300_000, millis)));
        } catch (NumberFormatException exception) {
            return fallback;
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

        private final CrossrefRequestGate gate;
        private boolean closed;

        private Permit(CrossrefRequestGate gate) {
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
