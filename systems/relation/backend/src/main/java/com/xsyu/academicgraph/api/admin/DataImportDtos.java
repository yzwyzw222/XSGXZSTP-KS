package com.xsyu.academicgraph.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据导入的请求/响应 DTO（管理员功能）。
 * 导入格式为「论文为中心」的嵌套 JSON：一篇论文把它的作者、机构、关键词、渠道都带在
 * 自己的字段里，服务端按名称自动去重——库里已有同名记录就复用 id，没有就新建，
 * 因此同一份文件可以重复导入（第二次导入时论文按标题/DOI 判重会被跳过）。
 */
public final class DataImportDtos {

    private DataImportDtos() {
    }

    /** 嵌套机构：作者署名机构按名称去重复用 */
    public record ImportInstitutionItem(
            @NotBlank(message = "机构名称不能为空") @Size(max = 255, message = "机构名称最长 255 字符") String name,
            @Size(max = 16, message = "国家代码最长 16 字符") String countryCode
    ) {
    }

    /** 嵌套作者：姓名 + 可选 ORCID + 可选署名机构 */
    public record ImportAuthorItem(
            @NotBlank(message = "作者姓名不能为空") @Size(max = 255, message = "作者姓名最长 255 字符") String name,
            @Size(max = 64, message = "ORCID 最长 64 字符") String orcid,
            ImportInstitutionItem institution
    ) {
    }

    /** 嵌套关键词：名称 + 可选研究领域 */
    public record ImportKeywordItem(
            @NotBlank(message = "关键词不能为空") @Size(max = 128, message = "关键词最长 128 字符") String name,
            @Size(max = 64, message = "研究领域最长 64 字符") String fieldName
    ) {
    }

    /** 嵌套渠道：名称 + 可选类型/ISSN */
    public record ImportVenueItem(
            @NotBlank(message = "渠道名称不能为空") @Size(max = 255, message = "渠道名称最长 255 字符") String name,
            @Size(max = 32, message = "渠道类型最长 32 字符") String venueType,
            @Size(max = 32, message = "ISSN 最长 32 字符") String issn
    ) {
    }

    /** 导入的论文条目：字段与数据库 paper 表对齐，嵌套的关联对象由服务端解析成 id */
    public record ImportPaperItem(
            @NotBlank(message = "论文标题不能为空") @Size(max = 500, message = "论文标题最长 500 字符") String title,
            @Size(max = 255, message = "DOI 最长 255 字符") String doi,
            String paperType,
            @Size(max = 16, message = "语种最长 16 字符") String language,
            LocalDate publicationDate,
            String abstractText,
            Integer citationCount,
            ImportVenueItem venue,
            @NotEmpty(message = "论文必须至少有一位署名作者") List<@Valid ImportAuthorItem> authors,
            List<@Valid ImportKeywordItem> keywords,
            @Size(max = 32, message = "卷最长 32 字符") String volume,
            @Size(max = 32, message = "期号最长 32 字符") String period,
            @Size(max = 32, message = "页码最长 32 字符") String pageCount,
            @Size(max = 64, message = "分类号最长 64 字符") String clcNumber,
            @Size(max = 500, message = "链接最长 500 字符") String url,
            List<@Valid ImportReferenceItem> references
    ) {
    }

    /**
     * 外部引用线索：被引文献不在库内时只记一条线索（DOI 或 OpenAlex id URL）。
     * 信息采集导入时 OpenAlex 的 referenced_works 是 id URL 而非 DOI，统一放进 externalDoi。
     */
    public record ImportReferenceItem(
            @NotBlank(message = "引用线索不能为空") @Size(max = 255, message = "引用线索最长 255 字符") String externalDoi
    ) {
    }

    /** 导入请求体：papers 数组（文件 JSON 的根结构） */
    public record ImportRequest(
            @NotEmpty(message = "papers 数组不能为空") List<@Valid ImportPaperItem> papers
    ) {
    }

    /** 单篇论文的导入结果行：状态为 IMPORTED / SKIPPED / FAILED */
    public record ImportResultRow(
            int index,
            String status,
            String message,
            Long paperId,
            String title
    ) {
    }

    /** 导入汇总：总条数 + 各状态计数 + 新建关联对象计数 + 明细行 */
    public record ImportSummary(
            int total,
            int imported,
            int skipped,
            int failed,
            int createdAuthors,
            int createdInstitutions,
            int createdKeywords,
            int createdVenues,
            List<ImportResultRow> rows
    ) {
    }
}
