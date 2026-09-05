package com.aacv.system.analytics.domain;

public record AnalyticsCoverage(
        long withDoiCount, long withPublicationYearCount, long withAbstractCount,
        long withCitationCount, long withOpenAccessStatusCount, long withRetractionStatusCount,
        long authorshipsMayBeIncompleteCount) { }
