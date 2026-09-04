package com.aacv.system.source.infrastructure.persistence;

import com.aacv.system.shared.domain.PageResult;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceProbeResult;
import com.aacv.system.source.domain.SourceType;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDataSourceRepository implements DataSourceRepository {

    private final DataSourceMapper mapper;

    public MyBatisDataSourceRepository(DataSourceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PageResult<DataSourceConfiguration> findPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        return PageResult.of(
                mapper.findPage((long) page * size, size).stream().map(this::toDomain).toList(),
                page,
                size,
                mapper.countAll());
    }

    @Override
    public Optional<DataSourceConfiguration> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::toDomain);
    }

    @Override
    public Optional<DataSourceConfiguration> lockById(long id) {
        return Optional.ofNullable(mapper.lockById(id)).map(this::toDomain);
    }

    @Override
    public boolean existsByCode(String sourceCode) {
        return mapper.countByCode(sourceCode) > 0;
    }

    @Override
    public DataSourceConfiguration insert(DataSourceConfiguration configuration) {
        DataSourceRow row = toRow(configuration);
        mapper.insert(row);
        return findById(row.getId()).orElseThrow();
    }

    @Override
    public Optional<DataSourceConfiguration> update(
            DataSourceConfiguration configuration, long expectedVersion) {
        if (mapper.update(toRow(configuration), expectedVersion) != 1) {
            return Optional.empty();
        }
        return findById(configuration.id());
    }

    @Override
    public Optional<DataSourceConfiguration> updateEnabled(long id, boolean enabled, long expectedVersion) {
        if (mapper.updateEnabled(id, enabled, expectedVersion) != 1) {
            return Optional.empty();
        }
        return findById(id);
    }

    @Override
    public void updateProbeResult(long id, SourceProbeResult result) {
        mapper.updateProbeResult(id, result.reachable(), result.checkedAt());
    }

    private DataSourceConfiguration toDomain(DataSourceRow row) {
        return new DataSourceConfiguration(
                row.getId(),
                row.getSourceCode(),
                SourceType.valueOf(row.getSourceType()),
                URI.create(row.getBaseUrl()),
                row.isEnabled(),
                new SourceConnectionSettings(
                        row.getRequestsPerSecond(),
                        row.getMaxConcurrency(),
                        Duration.ofSeconds(row.getConnectTimeoutSeconds()),
                        Duration.ofSeconds(row.getResponseTimeoutSeconds()),
                        row.getMaxRetries(),
                        row.getMaxResponseBytes()),
                row.getComplianceNote(),
                row.getLastSuccessAt(),
                row.getLastFailureAt(),
                row.getConsecutiveFailures(),
                row.getVersion(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private DataSourceRow toRow(DataSourceConfiguration configuration) {
        DataSourceRow row = new DataSourceRow();
        row.setId(configuration.id() == 0 ? null : configuration.id());
        row.setSourceCode(configuration.sourceCode());
        row.setSourceType(configuration.sourceType().name());
        row.setBaseUrl(configuration.baseUri().toString());
        row.setEnabled(configuration.enabled());
        row.setRequestsPerSecond(configuration.settings().requestsPerSecond());
        row.setMaxConcurrency(configuration.settings().maxConcurrency());
        row.setConnectTimeoutSeconds(Math.toIntExact(configuration.settings().connectTimeout().toSeconds()));
        row.setResponseTimeoutSeconds(Math.toIntExact(configuration.settings().responseTimeout().toSeconds()));
        row.setMaxRetries(configuration.settings().maxRetries());
        row.setMaxResponseBytes(configuration.settings().maxResponseBytes());
        row.setComplianceNote(configuration.complianceNote());
        row.setLastSuccessAt(configuration.lastSuccessAt());
        row.setLastFailureAt(configuration.lastFailureAt());
        row.setConsecutiveFailures(configuration.consecutiveFailures());
        row.setVersion(configuration.version());
        row.setCreatedAt(configuration.createdAt());
        row.setUpdatedAt(configuration.updatedAt());
        return row;
    }
}
