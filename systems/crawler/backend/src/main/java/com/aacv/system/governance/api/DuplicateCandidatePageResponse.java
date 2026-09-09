package com.aacv.system.governance.api;

import com.aacv.system.governance.domain.DuplicateCandidate;
import com.aacv.system.shared.domain.PageResult;
import java.util.List;

public record DuplicateCandidatePageResponse(
        List<DuplicateCandidateResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static DuplicateCandidatePageResponse from(PageResult<DuplicateCandidate> result) {
        return new DuplicateCandidatePageResponse(
                result.items().stream().map(DuplicateCandidateResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
