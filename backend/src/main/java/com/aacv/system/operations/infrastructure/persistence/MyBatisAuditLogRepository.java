package com.aacv.system.operations.infrastructure.persistence;

import com.aacv.system.operations.application.port.AuditLogRepository;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditLogEntry;
import com.aacv.system.operations.domain.AuditRecord;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.domain.PageResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class MyBatisAuditLogRepository implements AuditLogRepository {

    private static final TypeReference<Map<String, String>> SUMMARY_TYPE = new TypeReference<>() {
    };

    private final AuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisAuditLogRepository(AuditLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(AuditRecord record) {
        AuditLogRow row = new AuditLogRow();
        row.setActorUserId(record.actorUserId());
        row.setAction(record.action().name());
        row.setTargetType(record.targetType());
        row.setTargetId(record.targetId());
        row.setResult(record.result().name());
        row.setTraceId(record.traceId());
        row.setSummaryJson(objectMapper.writeValueAsString(record.summary()));
        row.setCreatedAt(record.createdAt());
        if (mapper.insert(row) != 1) {
            throw new IllegalStateException("审计记录写入数量异常");
        }
    }

    @Override
    public PageResult<AuditLogEntry> findPage(int page, int size) {
        long total = mapper.countAll();
        long offset = Math.multiplyExact((long) page, size);
        List<AuditLogEntry> items = mapper.findPage(offset, size).stream().map(this::toDomain).toList();
        return PageResult.of(items, page, size, total);
    }

    private AuditLogEntry toDomain(AuditLogRow row) {
        Map<String, String> summary = row.getSummaryJson() == null
                ? Map.of()
                : objectMapper.readValue(row.getSummaryJson(), SUMMARY_TYPE);
        return new AuditLogEntry(
                row.getId(),
                row.getActorUserId(),
                AuditAction.valueOf(row.getAction()),
                row.getTargetType(),
                row.getTargetId(),
                AuditResult.valueOf(row.getResult()),
                row.getTraceId(),
                summary,
                row.getCreatedAt());
    }
}
