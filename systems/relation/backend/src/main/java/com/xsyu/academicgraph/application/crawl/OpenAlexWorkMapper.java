package com.xsyu.academicgraph.application.crawl;

import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportAuthorItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportInstitutionItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportKeywordItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportPaperItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportReferenceItem;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportVenueItem;
import com.xsyu.academicgraph.domain.academic.Paper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OpenAlex 成果 → 平台导入条目的映射器（feature/Luo 的 OpenAlexWorkNormalizer 简化移植）。
 * 映射规则：
 *  - 论文类型：type=article → JOURNAL_ARTICLE（渠道类型是 conference 时改判 CONFERENCE_PAPER）；
 *    其余类型（preprint/book…）一律 OTHER——平台 paper 表只认这四种白名单
 *  - DOI/ORCID：OpenAlex 给的是 https://doi.org/… 全 URL，剥掉前缀存裸标识符
 *  - 渠道：primary_location.source → venue（名称/类型/ISSN-L）
 *  - 作者：authorships 逐个映射，每位作者只取第一个署名机构（与导入格式对齐）
 *  - 关键词：topics → keyword，OpenAlex 的 field.display_name（大学科领域）存进 field_name
 *  - 引用：referenced_works 是 OpenAlex id URL 而非 DOI——存进 references.externalDoi，
 *    落库时写 paper_reference.external_cited_doi（该列语义本来就是"外部线索"）
 *  - 被引次数：cited_by_count → citation_count
 */
@Component
public class OpenAlexWorkMapper {

    /** 把一条 OpenAlex 成果转成导入条目；作者/关键词等列表在导入服务里还会做名称去重 */
    public ImportPaperItem toImportItem(SourceWork work) {
        ImportVenueItem venue = work.venue() == null ? null
                : new ImportVenueItem(work.venue().displayName(), work.venue().type(), work.venue().issnL());
        List<ImportAuthorItem> authors = work.authorships().stream()
                .map(this::toAuthorItem)
                .toList();
        List<ImportKeywordItem> keywords = work.topics().stream()
                .map(topic -> new ImportKeywordItem(topic.displayName(), topic.fieldName()))
                .toList();
        List<ImportReferenceItem> references = work.referencedWorkIds().stream()
                .map(ImportReferenceItem::new)
                .toList();
        return new ImportPaperItem(
                work.title(),
                stripDoiPrefix(work.doi()),
                mapPaperType(work),
                work.language(),
                work.publicationDate(),
                work.abstractText(),
                work.citedByCount(),
                venue,
                authors,
                keywords,
                null, null, null, null, null,
                references);
    }

    /** 作者 → 导入条目：只带第一个署名机构（平台导入格式一位作者对应一个机构字段） */
    private ImportAuthorItem toAuthorItem(SourceWork.SourceAuthorship authorship) {
        ImportInstitutionItem institution = null;
        if (!authorship.organizations().isEmpty()) {
            SourceWork.SourceOrganization org = authorship.organizations().get(0);
            institution = new ImportInstitutionItem(org.displayName(), org.countryCode());
        }
        return new ImportAuthorItem(
                authorship.authorDisplayName(),
                stripOrcidPrefix(authorship.orcid()),
                institution);
    }

    /** OpenAlex type + 渠道类型 → 平台论文类型白名单 */
    private String mapPaperType(SourceWork work) {
        if ("article".equals(work.type())) {
            return work.venue() != null && "conference".equals(work.venue().type())
                    ? Paper.TYPE_CONFERENCE : Paper.TYPE_JOURNAL;
        }
        return Paper.TYPE_OTHER;
    }

    /** "https://doi.org/10.xxx" → "10.xxx"；非 URL 形态的 DOI 原样返回 */
    private String stripDoiPrefix(String doi) {
        return stripUrlPrefix(doi, "https://doi.org/");
    }

    /** "https://orcid.org/0000-…" → "0000-…"；空值保持 null */
    private String stripOrcidPrefix(String orcid) {
        return stripUrlPrefix(orcid, "https://orcid.org/");
    }

    private String stripUrlPrefix(String value, String prefix) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }
}
