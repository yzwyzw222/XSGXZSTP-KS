package com.aacv.system.graph.application;

import com.aacv.system.graph.domain.GraphTypeDefinition;
import com.aacv.system.graph.domain.GraphTypeDefinition.Kind;
import com.aacv.system.graph.infrastructure.persistence.GraphTypeMapper;
import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraphTypeService {
    private final GraphTypeMapper mapper;
    private final AuditService auditService;

    public GraphTypeService(GraphTypeMapper mapper, AuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('GRAPH_READ')")
    public List<GraphTypeDefinition> list() {
        return mapper.findAll();
    }

    /** 类型代码由固定图模型定义；版本校验防止并发编辑覆盖别人的审核结果。 */
    @Transactional
    @PreAuthorize("hasAuthority('GRAPH_SYNC_MANAGE')")
    public GraphTypeDefinition update(GraphTypeDefinition value) {
        validate(value);
        if (mapper.find(value.kind(), value.code()) == null) {
            throw new ResourceNotFoundException("图谱类型不存在");
        }
        GraphTypeDefinition normalized = new GraphTypeDefinition(value.kind(), value.code(),
                value.displayName().strip(), value.color(), value.size(), value.reviewStatus(), value.version());
        if (mapper.update(normalized) != 1) {
            throw new ResourceConflictException("类型配置已被修改，请刷新后重试");
        }
        auditService.record(AuditAction.GRAPH_TYPE_UPDATED, "GRAPH_TYPE", value.kind() + ":" + value.code(),
                AuditResult.SUCCESS, Map.of("reviewStatus", value.reviewStatus().name(),
                        "version", Long.toString(value.version() + 1)));
        return mapper.find(value.kind(), value.code());
    }

    static void validate(GraphTypeDefinition value) {
        if (value == null || value.kind() == null || value.code() == null
                || !value.code().matches("[A-Z_]{1,32}") || value.displayName() == null
                || value.displayName().isBlank() || value.displayName().strip().length() > 64
                || value.displayName().codePoints().anyMatch(Character::isISOControl)
                || value.color() == null || !value.color().matches("#[0-9a-fA-F]{6}")
                || value.reviewStatus() == null || value.version() < 0 || value.version() == Long.MAX_VALUE
                || value.size() < (value.kind() == Kind.NODE ? 16 : 1)
                || value.size() > (value.kind() == Kind.NODE ? 96 : 8)) {
            throw new IllegalArgumentException("图谱类型名称、颜色、尺寸、审核状态或版本无效");
        }
    }
}
