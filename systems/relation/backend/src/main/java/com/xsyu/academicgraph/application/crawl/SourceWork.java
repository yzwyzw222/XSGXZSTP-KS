package com.xsyu.academicgraph.application.crawl;

import java.time.LocalDate;
import java.util.List;

/**
 * OpenAlex 成果的领域模型（从 feature/Luo 的 SourceWork 移植的简化版）。
 * 一条 SourceWork = OpenAlex /works 搜索返回的一篇论文，保留导入链路用得到的字段：
 * 外部 id、DOI、标题、类型、语种、发表日期、发表渠道、署名作者、主题（→关键词）、
 * 参考文献列表（→外部引用线索）、重建后的摘要文本、被引次数。
 *
 * 原版还有字段告警列表、日期精度、学术元数据等完整采集才需要的字段，已按精简方案砍掉。
 */
public record SourceWork(
        String externalId,
        String doi,
        String title,
        String type,
        String language,
        LocalDate publicationDate,
        SourceVenue venue,
        List<SourceAuthorship> authorships,
        List<SourceTopic> topics,
        List<String> referencedWorkIds,
        String abstractText,
        Integer citedByCount) {

    public SourceWork {
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("OpenAlex 成果 id 不能为空");
        }
        authorships = authorships == null ? List.of() : List.copyOf(authorships);
        topics = topics == null ? List.of() : List.copyOf(topics);
        referencedWorkIds = referencedWorkIds == null ? List.of() : List.copyOf(referencedWorkIds);
    }

    /** 发表渠道：名称 / ISSN-L / OpenAlex 渠道类型（journal、conference、repository…） */
    public record SourceVenue(String externalId, String displayName, String issnL, String type) {
    }

    /** 署名作者：位次 + 作者姓名 + ORCID + 署名机构列表（按 OpenAlex 原顺序） */
    public record SourceAuthorship(
            int position,
            String authorExternalId,
            String authorDisplayName,
            String orcid,
            List<SourceOrganization> organizations) {

        public SourceAuthorship {
            if (position < 1) {
                throw new IllegalArgumentException("作者位次必须从 1 开始");
            }
            organizations = organizations == null ? List.of() : List.copyOf(organizations);
        }
    }

    /** 署名机构：名称 / 国家代码 / 机构类型（education、company…） */
    public record SourceOrganization(String externalId, String displayName, String countryCode, String type) {
    }

    /** OpenAlex 主题（Topic）：名称 + 所属子领域 + 所属大学科领域（→ 平台关键词的 field_name） */
    public record SourceTopic(String externalId, String displayName, String subfieldName, String fieldName) {
    }
}
