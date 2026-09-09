package com.xsyu.academicgraph.api.keywords;

import com.xsyu.academicgraph.domain.academic.Keyword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 关键词接口的请求/响应 DTO。
 * keyword 表没有 version 列，因此这里不做乐观锁（更新直接覆盖）。
 */
public final class KeywordDtos {

    private KeywordDtos() {
    }

    /** 创建/更新关键词请求体 */
    public record KeywordUpsertRequest(
            @NotBlank(message = "关键词不能为空")
            @Size(max = 255, message = "关键词不能超过 255 字")
            String name,

            @Size(max = 255, message = "学科领域不能超过 255 字")
            String fieldName
    ) {
    }

    /** 关键词详情响应 */
    public record KeywordResponse(
            Long id,
            String name,
            String fieldName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        /** 实体 → 响应 DTO */
        public static KeywordResponse from(Keyword keyword) {
            return new KeywordResponse(keyword.getId(), keyword.getName(), keyword.getFieldName(),
                    keyword.getCreatedAt(), keyword.getUpdatedAt());
        }
    }
}
