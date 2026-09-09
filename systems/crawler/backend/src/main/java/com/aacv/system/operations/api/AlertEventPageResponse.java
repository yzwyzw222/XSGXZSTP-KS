package com.aacv.system.operations.api;

import com.aacv.system.operations.domain.AlertEvent;
import com.aacv.system.shared.domain.PageResult;
import java.util.List;

public record AlertEventPageResponse(
        List<AlertEventResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static AlertEventPageResponse from(PageResult<AlertEvent> result) {
        return new AlertEventPageResponse(
                result.items().stream().map(AlertEventResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
