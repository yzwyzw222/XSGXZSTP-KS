package com.aacv.system.crawl.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class CrawlRunStateMachine {

    private static final Map<CrawlRunStatus, Set<CrawlRunStatus>> TRANSITIONS = transitions();

    private CrawlRunStateMachine() {
    }

    public static boolean canTransition(CrawlRunStatus from, CrawlRunStatus to) {
        return from != null && to != null && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(CrawlRunStatus from, CrawlRunStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("非法采集运行状态转换：" + from + " -> " + to);
        }
    }

    public static boolean isTerminal(CrawlRunStatus status) {
        return status == CrawlRunStatus.SUCCEEDED
                || status == CrawlRunStatus.PARTIAL_SUCCESS
                || status == CrawlRunStatus.FAILED
                || status == CrawlRunStatus.CANCELLED;
    }

    private static Map<CrawlRunStatus, Set<CrawlRunStatus>> transitions() {
        EnumMap<CrawlRunStatus, Set<CrawlRunStatus>> values = new EnumMap<>(CrawlRunStatus.class);
        values.put(CrawlRunStatus.PENDING, EnumSet.of(
                CrawlRunStatus.RUNNING, CrawlRunStatus.CANCELLING,
                CrawlRunStatus.CANCELLED, CrawlRunStatus.FAILED));
        values.put(CrawlRunStatus.RUNNING, EnumSet.of(
                CrawlRunStatus.PAUSING, CrawlRunStatus.CANCELLING,
                CrawlRunStatus.SUCCEEDED, CrawlRunStatus.PARTIAL_SUCCESS, CrawlRunStatus.FAILED));
        values.put(CrawlRunStatus.PAUSING, EnumSet.of(
                CrawlRunStatus.PAUSED, CrawlRunStatus.CANCELLING, CrawlRunStatus.FAILED));
        values.put(CrawlRunStatus.PAUSED, EnumSet.of(CrawlRunStatus.RUNNING, CrawlRunStatus.CANCELLING));
        values.put(CrawlRunStatus.CANCELLING, EnumSet.of(CrawlRunStatus.CANCELLED, CrawlRunStatus.FAILED));
        return Map.copyOf(values);
    }
}
