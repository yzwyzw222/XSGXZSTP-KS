package com.aacv.system.source.application.port;

import com.aacv.system.crawl.domain.CrawlScope;
import com.aacv.system.ingestion.domain.RawSourceRecord;
import com.aacv.system.source.domain.OpaqueCursor;
import com.aacv.system.source.domain.SourceCapabilities;
import com.aacv.system.source.domain.SourceConnectionSettings;
import com.aacv.system.source.domain.SourcePage;
import com.aacv.system.source.domain.SourceProbeResult;
import com.aacv.system.source.domain.SourceType;
import com.aacv.system.source.domain.SourceValidationResult;
import com.aacv.system.source.domain.SourceWork;

public interface DataSourceAdapter {

    SourceType sourceType();

    default String parserVersion() {
        return sourceType().name().toLowerCase(java.util.Locale.ROOT) + "-v1";
    }

    SourceValidationResult validate(SourceConnectionSettings settings, CrawlScope scope);

    SourceProbeResult probe(SourceConnectionSettings settings);

    SourcePage fetchPage(SourceConnectionSettings settings, CrawlScope scope, OpaqueCursor cursor);

    SourceWork parse(RawSourceRecord rawRecord);

    SourceCapabilities capabilities();
}
