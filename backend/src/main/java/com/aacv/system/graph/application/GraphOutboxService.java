package com.aacv.system.graph.application;

import com.aacv.system.graph.application.port.GraphProjectionRequestPort;
import com.aacv.system.graph.domain.GraphOutboxEvent;
import com.aacv.system.graph.infrastructure.persistence.GraphOutboxMapper;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraphOutboxService {

    public static final int MAX_ATTEMPTS = 5;
    private static final Duration BASE_RETRY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY = Duration.ofMinutes(30);

    private final GraphOutboxMapper mapper;
    private final GraphProjectionRequestPort requestPort;
    private final Clock clock;

    public GraphOutboxService(
            GraphOutboxMapper mapper, GraphProjectionRequestPort requestPort, Clock clock) {
        this.mapper = mapper;
        this.requestPort = requestPort;
        this.clock = clock;
    }

    @Transactional
    public List<GraphOutboxEvent> claim(String workerId, int limit, Duration lease) {
        if (workerId == null || workerId.isBlank() || limit < 1 || limit > 50
                || lease == null || lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("Outbox认领参数无效");
        }
        Instant now = clock.instant();
        if (mapper.rebuildInProgress()) {
            return List.of();
        }
        mapper.recoverExpiredLeases(now);
        List<Long> ids = mapper.findClaimableIds(now, limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        if (mapper.claimIds(ids, workerId, now.plus(lease)) != ids.size()) {
            throw new IllegalStateException("Outbox认领数量异常");
        }
        return mapper.findClaimed(ids, workerId);
    }

    @Transactional
    public void succeed(GraphOutboxEvent event, String workerId) {
        Instant now = clock.instant();
        if (mapper.markEventSucceeded(event.id(), workerId, now) != 1) {
            throw new ResourceConflictException("Outbox租约已失效");
        }
        if (mapper.advanceAppliedVersion(event.achievementId(), event.desiredVersion(), now) != 1) {
            throw new IllegalStateException("图投影已应用版本更新数量异常");
        }
    }

    @Transactional
    public void fail(GraphOutboxEvent event, String workerId, String errorCode) {
        String checkedCode = safeCode(errorCode);
        String summary = "Neo4j图投影执行失败";
        Instant now = clock.instant();
        if (event.attempts() + 1 >= MAX_ATTEMPTS) {
            if (mapper.markDead(event.id(), workerId, checkedCode, summary, now) != 1) {
                throw new ResourceConflictException("Outbox租约已失效");
            }
            if (mapper.insertDeadLetter(event.eventId(), checkedCode, summary, now) != 1) {
                throw new IllegalStateException("Outbox死信写入数量异常");
            }
            return;
        }
        Instant nextAttemptAt = now.plus(retryDelay(event.eventId(), event.attempts() + 1));
        if (mapper.markRetry(event.id(), workerId, nextAttemptAt, checkedCode, summary) != 1) {
            throw new ResourceConflictException("Outbox租约已失效");
        }
    }

    @Transactional
    public String replay(String eventId) {
        Long achievementId = mapper.findDeadAchievementId(eventId);
        if (achievementId == null) {
            throw new ResourceNotFoundException("待重放图事件不存在");
        }
        String replayEventId = requestPort.requestAchievement(achievementId);
        if (mapper.linkReplay(eventId, replayEventId) != 1) {
            throw new ResourceConflictException("图事件已被重放");
        }
        return replayEventId;
    }

    private Duration retryDelay(String eventId, int attempts) {
        long baseSeconds = Math.min(
                MAX_RETRY.toSeconds(), BASE_RETRY.toSeconds() * (1L << Math.max(0, attempts - 1)));
        long jitterPercent = Math.floorMod(eventId.hashCode(), 41) - 20L;
        return Duration.ofSeconds(Math.max(1, baseSeconds + baseSeconds * jitterPercent / 100));
    }

    private String safeCode(String errorCode) {
        if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,64}")) {
            return "GRAPH_PROJECTION_FAILED";
        }
        return errorCode;
    }
}
