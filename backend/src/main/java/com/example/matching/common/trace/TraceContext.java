package com.example.matching.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";

    private TraceContext() {
    }

    public static String current() {
        String existing = MDC.get(TRACE_ID_KEY);
        if (existing == null || existing.isBlank()) {
            existing = "TRC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            MDC.put(TRACE_ID_KEY, existing);
        }
        return existing;
    }

    public static void set(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }

    public static String getOrNull() {
        return MDC.get(TRACE_ID_KEY);
    }
}
