package com.aacv.system.operations.application.port;

import com.aacv.system.operations.domain.AuditQuery;

import com.aacv.system.operations.domain.AuditLogEntry;
import com.aacv.system.operations.domain.AuditRecord;
import com.aacv.system.shared.domain.PageResult;

public interface AuditLogRepository {

    void append(AuditRecord record);

    PageResult<AuditLogEntry> findPage(int page, int size, AuditQuery query);
}
