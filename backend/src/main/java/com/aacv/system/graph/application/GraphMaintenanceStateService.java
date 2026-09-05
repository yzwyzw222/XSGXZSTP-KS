package com.aacv.system.graph.application;

import com.aacv.system.graph.infrastructure.persistence.GraphMaintenanceMapper;
import com.aacv.system.shared.application.ResourceConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class GraphMaintenanceStateService {

    private final GraphMaintenanceMapper mapper;

    public GraphMaintenanceStateService(GraphMaintenanceMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public long currentCursor(long runId) {
        // 使用独立SqlSession读取分页已提交的进度，不复用长任务会话中的一级缓存。
        var run = mapper.findRun(runId);
        if (run == null) {
            throw new ResourceConflictException("图维护运行不存在");
        }
        return run.cursorAchievementId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(long runId) {
        if (mapper.markRunning(runId) != 1) {
            throw new ResourceConflictException("图维护运行无法启动");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(long runId) {
        if (mapper.markSucceeded(runId) != 1) {
            throw new ResourceConflictException("图维护运行无法完成");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long runId, String errorCode, String summary) {
        mapper.markFailed(runId, errorCode, summary);
    }
}
