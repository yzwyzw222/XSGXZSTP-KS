package com.aacv.system.source.application;

import com.aacv.system.shared.application.ResourceNotFoundException;
import com.aacv.system.source.application.port.DataSourceRepository;
import com.aacv.system.source.application.port.SourceEntityLookup;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceEntity;
import com.aacv.system.source.domain.SourceType;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class SourceEntityService {
    private final DataSourceRepository repository;
    private final SourceEntityLookup lookup;

    public SourceEntityService(DataSourceRepository repository, SourceEntityLookup lookup) {
        this.repository = repository;
        this.lookup = lookup;
    }

    @PreAuthorize("hasAuthority('SOURCE_READ')")
    public List<SourceEntity> search(long sourceId, String entityKind, String query) {
        var kind = SourceEntity.Kind.fromPath(entityKind);
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty() || normalized.length() > 200
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("搜索名称须为1至200个字符，且不能包含控制字符");
        }
        return lookup.search(settings(sourceId), kind, normalized);
    }

    @PreAuthorize("hasAuthority('SOURCE_READ')")
    public List<SourceEntity> resolve(long sourceId, String entityKind, List<String> ids) {
        var kind = SourceEntity.Kind.fromPath(entityKind);
        if (ids == null || ids.isEmpty() || ids.size() > 50) {
            throw new IllegalArgumentException("回显名称须提供1至50个标识");
        }
        var normalized = ids.stream().map(kind::normalizeId).distinct().toList();
        return lookup.resolve(settings(sourceId), kind, normalized);
    }

    // 外部查询不占用数据库事务，沿用来源读取权限和启用状态。
    private SourceConnectionSettings settings(long sourceId) {
        var source = repository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("数据源不存在"));
        if (!source.enabled()) throw new IllegalArgumentException("数据源已停用");
        if (source.sourceType() != SourceType.OPENALEX) {
            throw new IllegalArgumentException("当前数据源不支持作者或机构名称查询");
        }
        return source.settings();
    }
}
