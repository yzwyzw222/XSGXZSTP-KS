package com.aacv.system.source.application.port;

import com.aacv.system.shared.domain.PageResult;
import com.aacv.system.source.domain.DataSourceConfiguration;
import com.aacv.system.source.domain.SourceProbeResult;
import java.util.Optional;

public interface DataSourceRepository {

    PageResult<DataSourceConfiguration> findPage(int page, int size);

    Optional<DataSourceConfiguration> findById(long id);

    Optional<DataSourceConfiguration> lockById(long id);

    boolean existsByCode(String sourceCode);

    DataSourceConfiguration insert(DataSourceConfiguration configuration);

    Optional<DataSourceConfiguration> update(DataSourceConfiguration configuration, long expectedVersion);

    Optional<DataSourceConfiguration> updateEnabled(long id, boolean enabled, long expectedVersion);

    void updateProbeResult(long id, SourceProbeResult result);
}
