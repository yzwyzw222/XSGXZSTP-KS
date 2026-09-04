package com.aacv.system.operations.api;

import com.aacv.system.operations.domain.AuditLogEntry;
import com.aacv.system.shared.domain.PageResult;
import java.util.List;

public record AuditPageResponse(
        List<AuditLogResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static AuditPageResponse from(PageResult<AuditLogEntry> result) {
        return new AuditPageResponse(
                result.items().stream().map(AuditLogResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
