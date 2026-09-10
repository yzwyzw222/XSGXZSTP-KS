package com.aacv.system.source.application.port;

import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourceEntity;
import java.util.List;

public interface SourceEntityLookup {
    List<SourceEntity> search(SourceConnectionSettings settings, SourceEntity.Kind kind, String query);
    List<SourceEntity> resolve(SourceConnectionSettings settings, SourceEntity.Kind kind, List<String> ids);
}
