package com.aacv.system.source.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record DataSourceConfiguration(
        long id,
        String sourceCode,
        SourceType sourceType,
        URI baseUri,
        boolean enabled,
        SourceConnectionSettings settings,
        String complianceNote,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        int consecutiveFailures,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static final String OPENALEX_CODE = "OPENALEX";
    public static final URI OPENALEX_BASE_URI = URI.create("https://api.openalex.org");
    public static final String CROSSREF_CODE = "CROSSREF";
    public static final URI CROSSREF_BASE_URI = URI.create("https://api.crossref.org");

    private static final Map<SourceType, FixedIdentity> FIXED_IDENTITIES = Map.of(
            SourceType.OPENALEX, new FixedIdentity(OPENALEX_CODE, OPENALEX_BASE_URI),
            SourceType.CROSSREF, new FixedIdentity(CROSSREF_CODE, CROSSREF_BASE_URI));

    public DataSourceConfiguration {
        FixedIdentity identity = FIXED_IDENTITIES.get(sourceType);
        if (identity == null || !identity.sourceCode().equals(sourceCode)
                || !identity.baseUri().equals(baseUri)) {
            throw new IllegalArgumentException("数据源类型、代码和官方基础地址不匹配");
        }
        if (settings == null || complianceNote == null || complianceNote.isBlank()
                || complianceNote.length() > 1000) {
            throw new IllegalArgumentException("数据源配置或合规说明无效");
        }
        if (consecutiveFailures < 0 || version < 0) {
            throw new IllegalArgumentException("数据源状态无效");
        }
    }

    public static String sourceCode(SourceType sourceType) {
        return requireIdentity(sourceType).sourceCode();
    }

    public static URI baseUri(SourceType sourceType) {
        return requireIdentity(sourceType).baseUri();
    }

    private static FixedIdentity requireIdentity(SourceType sourceType) {
        FixedIdentity identity = FIXED_IDENTITIES.get(sourceType);
        if (identity == null) {
            throw new IllegalArgumentException("不支持的数据源类型");
        }
        return identity;
    }

    private record FixedIdentity(String sourceCode, URI baseUri) {
    }
}
