package com.xsyu.academicgraph.api.authors;

import com.xsyu.academicgraph.domain.academic.Author;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 作者接口的请求/响应 DTO。
 * 创建与更新共用 UpsertRequest：更新时额外携带 version 做乐观锁校验（与库中不一致返回 409）。
 */
public final class AuthorDtos {

    private AuthorDtos() {
    }

    /** 创建/更新作者请求体 */
    public record AuthorUpsertRequest(
            @NotBlank(message = "作者姓名不能为空")
            @Size(max = 255, message = "作者姓名不能超过 255 字")
            String displayName,

            @Size(max = 64, message = "ORCID 不能超过 64 字")
            String orcid,

            /** 乐观锁版本号：更新时必填 */
            Long version
    ) {
    }

    /** 作者详情响应 */
    public record AuthorResponse(
            Long id,
            String displayName,
            String orcid,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /** 实体 → 响应 DTO */
        public static AuthorResponse from(Author author) {
            return new AuthorResponse(author.getId(), author.getDisplayName(), author.getOrcid(),
                    author.getVersion(), author.getCreatedAt(), author.getUpdatedAt());
        }
    }
}
