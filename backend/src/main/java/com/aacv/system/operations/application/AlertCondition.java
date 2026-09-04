package com.aacv.system.operations.application;

import com.aacv.system.operations.domain.AlertSeverity;
import com.aacv.system.operations.domain.AlertSubjectType;
import com.aacv.system.operations.domain.AlertType;
import java.time.Instant;
import java.util.Map;

record AlertCondition(
        AlertType type,
        AlertSeverity severity,
        AlertSubjectType subjectType,
        String subjectId,
        String summary,
        Map<String, Object> evidence,
        Instant signalAt) {

    String dedupKey() {
        return type.name() + ":" + subjectType.name() + ":" + (subjectId == null ? "SYSTEM" : subjectId);
    }
}
