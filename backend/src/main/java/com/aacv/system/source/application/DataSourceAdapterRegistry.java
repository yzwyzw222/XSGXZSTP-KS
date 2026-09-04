package com.aacv.system.source.application;

import com.aacv.system.shared.application.ResourceConflictException;
import com.aacv.system.source.application.port.DataSourceAdapter;
import com.aacv.system.source.domain.SourceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DataSourceAdapterRegistry {

    private final Map<SourceType, DataSourceAdapter> adapters;

    public DataSourceAdapterRegistry(List<DataSourceAdapter> adapters) {
        EnumMap<SourceType, DataSourceAdapter> registered = new EnumMap<>(SourceType.class);
        for (DataSourceAdapter adapter : adapters) {
            if (registered.put(adapter.sourceType(), adapter) != null) {
                throw new IllegalStateException("同一数据源类型只能注册一个适配器");
            }
        }
        this.adapters = Map.copyOf(registered);
    }

    public DataSourceAdapter require(SourceType sourceType) {
        DataSourceAdapter adapter = adapters.get(sourceType);
        if (adapter == null) {
            throw new ResourceConflictException("数据源适配器尚未就绪");
        }
        return adapter;
    }
}
