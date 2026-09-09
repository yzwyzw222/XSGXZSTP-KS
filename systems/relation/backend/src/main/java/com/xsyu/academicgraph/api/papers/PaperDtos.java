package com.xsyu.academicgraph.api.papers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 论文模块 DTO。
 * 提交时 authors/keywords/references 作为嵌套数组整体提交，服务层在同一事务里
 * 拆解到 paper_author / paper_keyword / paper_reference 三张关系表（全量替换语义）。
 */
public final class PaperDtos {

    private PaperDtos() {
    }

    /** 署名条目：作者 id + 位次 + 署名机构（机构可空） */
    public record AuthorItem(
            @NotNull(message = "作者 id 不能为空") Long authorId,
            @NotNull(message = "作者位次不能为空") @Min(value = 1, message = "作者位次从 1 开始") Integer position,
            Long institutionId
    ) {
    }

    /** 引用条目：被引论文在库内时给 id，不在库内时给外部 DOI 线索，两者至少一个 */
    public record ReferenceItem(
            Long citedPaperId,
            @Size(max = 255, message = "DOI 最长 255 字符") String externalCitedDoi
    ) {
    }

    /** 创建/更新论文请求（version 用于乐观锁，更新时必填） */
    public record PaperUpsertRequest(
            @NotBlank(message = "标题不能为空") @Size(max = 500, message = "标题最长 500 字符") String title,
            @Size(max = 255, message = "DOI 最长 255 字符") String doi,
            String paperType,
            @Size(max = 16, message = "语种最长 16 字符") String language,
            LocalDate publicationDate,
            String abstractText,
            @Min(value = 0, message = "被引次数不能为负数") Integer citationCount,
            Long venueId,
            // 知网导入扩展字段：可空字符串（期号含字母如 Z3、页码是区间、分类号多值分号分隔，故不用数值类型）
            @Size(max = 32, message = "卷最长 32 字符") String volume,
            @Size(max = 32, message = "期号最长 32 字符") String period,
            @Size(max = 32, message = "页码最长 32 字符") String pageCount,
            @Size(max = 64, message = "分类号最长 64 字符") String clcNumber,
            @Size(max = 500, message = "链接最长 500 字符") String url,
            @Valid List<AuthorItem> authors,
            List<@Positive(message = "关键词 id 必须为正数") Long> keywordIds,
            @Valid List<ReferenceItem> references,
            Long version
    ) {
    }

    public record VenueRef(Long id, String displayName) {
    }

    public record AuthorRef(Long authorId, String displayName, Integer position, Long institutionId, String institutionName) {
    }

    public record KeywordRef(Long id, String name) {
    }

    public record ReferenceRef(Long id, Long citedPaperId, String citedTitle, String externalCitedDoi) {
    }

    /** 论文详情响应 */
    public record PaperResponse(
            Long id,
            String title,
            String doi,
            String paperType,
            String language,
            LocalDate publicationDate,
            Short publicationYear,
            String abstractText,
            Integer citationCount,
            /** LLM 抽取状态（PENDING/IN_PROGRESS/COMPLETED/FAILED），实体抽取面板据此筛选 */
            String extractionStatus,
            VenueRef venue,
            String volume,
            String period,
            String pageCount,
            String clcNumber,
            String url,
            List<AuthorRef> authors,
            List<KeywordRef> keywords,
            List<ReferenceRef> references,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
