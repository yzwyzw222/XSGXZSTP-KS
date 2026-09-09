package com.xsyu.academicgraph.api.institutions;

import com.xsyu.academicgraph.domain.academic.Institution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 机构接口的请求/响应 DTO。
 * 创建与更新共用 UpsertRequest：更新时额外携带 version 做乐观锁校验（与库中不一致返回 409）。
 */
public final class InstitutionDtos {

    private InstitutionDtos() {
    }

    /** 创建/更新机构请求体 */
    public record InstitutionUpsertRequest(
            @NotBlank(message = "机构名称不能为空")
            @Size(max = 255, message = "机构名称不能超过 255 字")
            String displayName,

            @Size(max = 2, message = "国家码是 2 位 ISO 码（如 CN/US）")
            String countryCode,

            @Size(max = 64, message = "机构类型不能超过 64 字")
            String institutionType,

            /** 乐观锁版本号：更新时必填 */
            Long version
    ) {
    }

    /** 机构详情响应 */
    public record InstitutionResponse(
            Long id,
            String displayName,
            String countryCode,
            String institutionType,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /** 实体 → 响应 DTO */
        public static InstitutionResponse from(Institution institution) {
            return new InstitutionResponse(institution.getId(), institution.getDisplayName(),
                    institution.getCountryCode(), institution.getInstitutionType(),
                    institution.getVersion(), institution.getCreatedAt(), institution.getUpdatedAt());
        }
    }
}
