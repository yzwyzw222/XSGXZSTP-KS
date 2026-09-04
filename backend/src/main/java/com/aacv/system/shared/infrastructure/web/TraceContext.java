package com.aacv.system.shared.infrastructure.web;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

public final class TraceContext {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String ATTRIBUTE_NAME = TraceContext.class.getName() + ".traceId";
    private static final String MDC_KEY = "traceId";
    private static final Pattern TRUSTED_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private TraceContext() {
    }

    public static String normalizeOrCreate(String candidate) {
        if (candidate != null && TRUSTED_TRACE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void put(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null ? normalizeOrCreate(null) : traceId;
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
