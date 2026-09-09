package com.xsyu.academicgraph.api.venues;

import com.xsyu.academicgraph.domain.academic.Venue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 发表渠道（期刊/会议）接口的请求/响应 DTO。
 * venue 表没有 version 列，因此这里不做乐观锁（更新直接覆盖）。
 */
public final class VenueDtos {

    private VenueDtos() {
    }

    /** 创建/更新渠道请求体 */
    public record VenueUpsertRequest(
            @NotBlank(message = "渠道名称不能为空")
            @Size(max = 500, message = "渠道名称不能超过 500 字")
            String displayName,

            @Size(max = 16, message = "ISSN 不能超过 16 字")
            String issn,

            @Size(max = 64, message = "渠道类型不能超过 64 字（如 journal/conference）")
            String venueType
    ) {
    }

    /** 渠道详情响应 */
    public record VenueResponse(
            Long id,
            String displayName,
            String issn,
            String venueType,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /** 实体 → 响应 DTO */
        public static VenueResponse from(Venue venue) {
            return new VenueResponse(venue.getId(), venue.getDisplayName(), venue.getIssn(),
                    venue.getVenueType(), venue.getCreatedAt(), venue.getUpdatedAt());
        }
    }
}
