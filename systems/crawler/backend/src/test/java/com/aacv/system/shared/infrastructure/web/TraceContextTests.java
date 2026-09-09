package com.aacv.system.shared.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TraceContextTests {

    @Test
    void acceptsOnlyBoundedSafeExternalTraceIds() {
        assertEquals("trace_123456", TraceContext.normalizeOrCreate("trace_123456"));

        String generatedForShortValue = TraceContext.normalizeOrCreate("short");
        String generatedForUnsafeValue = TraceContext.normalizeOrCreate("trace value\nunsafe");

        assertNotEquals("short", generatedForShortValue);
        assertNotEquals("trace value\nunsafe", generatedForUnsafeValue);
        assertTrue(generatedForShortValue.matches("[0-9a-f]{32}"));
        assertTrue(generatedForUnsafeValue.matches("[0-9a-f]{32}"));
    }
}
