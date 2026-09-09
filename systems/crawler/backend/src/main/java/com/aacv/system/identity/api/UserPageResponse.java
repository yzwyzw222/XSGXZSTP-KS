package com.aacv.system.identity.api;

import com.aacv.system.identity.domain.UserAccount;
import com.aacv.system.shared.domain.PageResult;
import java.util.List;

public record UserPageResponse(
        List<UserResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static UserPageResponse from(PageResult<UserAccount> result) {
        return new UserPageResponse(
                result.items().stream().map(UserResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
