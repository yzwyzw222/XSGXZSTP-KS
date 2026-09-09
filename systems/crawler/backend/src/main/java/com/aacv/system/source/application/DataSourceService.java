package com.aacv.system.source.application;

import com.aacv.system.operations.application.AuditService;
import com.aacv.system.operations.domain.AuditAction;
import com.aacv.system.operations.domain.AuditResult;
import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.shared.application.ResourceNotFoundException;
import com.aacv.system.shared.domain.PageResult;
import com.aacv.system.source.application.port.DataSourceAdapter;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceProbeResult;
import com.aacv.system.source.domain.SourceType;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataSourceService {

    private final DataSourceRepository repository;
    private final DataSourceAdapterRegistry adapterRegistry;
    private final AuditService auditService;
    private final Clock clock;

    public DataSourceService(
            DataSourceRepository repository,
            DataSourceAdapterRegistry adapterRegistry,
            AuditService auditService,
            Clock clock) {
        this.repository = repository;
        this.adapterRegistry = adapterRegistry;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SOURCE_READ')")
    public PageResult<DataSourceConfiguration> findPage(int page, int size) {
        return repository.findPage(page, size);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SOURCE_READ')")
    public DataSourceConfiguration requireById(long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("数据源不存在"));
    }

    @Transactional
    @PreAuthorize("hasAuthority('SOURCE_MANAGE')")
    public DataSourceConfiguration create(
            SourceConnectionSettings settings,
            String complianceNote) {
        return create(SourceType.OPENALEX, settings, complianceNote);
    }

    @Transactional
    @PreAuthorize("hasAuthority('SOURCE_MANAGE')")
    public DataSourceConfiguration create(
            SourceType sourceType,
            SourceConnectionSettings settings,
            String complianceNote) {
        String sourceCode = DataSourceConfiguration.sourceCode(sourceType);
        if (repository.existsByCode(sourceCode)) {
            throw new ResourceConflictException(sourceCode + "数据源已经存在");
        }
        Instant now = clock.instant();
        DataSourceConfiguration created = repository.insert(new DataSourceConfiguration(
                0,
                sourceCode,
                sourceType,
                DataSourceConfiguration.baseUri(sourceType),
                true,
                settings,
                complianceNote.trim(),
                null,
                null,
                0,
                0,
                now,
                now));
        auditService.record(
                AuditAction.SOURCE_CREATED,
                "DATA_SOURCE",
                Long.toString(created.id()),
                AuditResult.SUCCESS,
                Map.of("sourceCode", created.sourceCode()));
        return created;
    }

    @Transactional
    @PreAuthorize("hasAuthority('SOURCE_MANAGE')")
    public DataSourceConfiguration update(
            long id,
            SourceConnectionSettings settings,
            String complianceNote,
            long expectedVersion) {
        DataSourceConfiguration current = repository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("数据源不存在"));
        DataSourceConfiguration candidate = new DataSourceConfiguration(
                current.id(), current.sourceCode(), current.sourceType(), current.baseUri(), current.enabled(),
                settings, complianceNote.trim(), current.lastSuccessAt(), current.lastFailureAt(),
                current.consecutiveFailures(), current.version(), current.createdAt(), clock.instant());
        DataSourceConfiguration updated = repository.update(candidate, expectedVersion)
                .orElseThrow(() -> new ResourceConflictException("数据源已被其他操作更新"));
        auditService.record(
                AuditAction.SOURCE_UPDATED,
                "DATA_SOURCE",
                Long.toString(id),
                AuditResult.SUCCESS,
                Map.of("sourceCode", updated.sourceCode()));
        return updated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('SOURCE_MANAGE')")
    public DataSourceConfiguration setEnabled(long id, boolean enabled, long expectedVersion) {
        repository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("数据源不存在"));
        DataSourceConfiguration updated = repository.updateEnabled(id, enabled, expectedVersion)
                .orElseThrow(() -> new ResourceConflictException("数据源已被其他操作更新"));
        auditService.record(
                enabled ? AuditAction.SOURCE_ENABLED : AuditAction.SOURCE_DISABLED,
                "DATA_SOURCE",
                Long.toString(id),
                AuditResult.SUCCESS,
                Map.of());
        return updated;
    }

    @Transactional
    @PreAuthorize("hasAuthority('SOURCE_PROBE')")
    public SourceProbeResult probe(long id) {
        DataSourceConfiguration source = repository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("数据源不存在"));
        DataSourceAdapter adapter = adapterRegistry.require(source.sourceType());
        SourceProbeResult result = adapter.probe(source.settings());
        repository.updateProbeResult(id, result);
        auditService.record(
                AuditAction.SOURCE_PROBED,
                "DATA_SOURCE",
                Long.toString(id),
                result.reachable() ? AuditResult.SUCCESS : AuditResult.FAILURE,
                Map.of("category", result.reachable() ? "reachable" : result.errorCategory()));
        return result;
    }
}
