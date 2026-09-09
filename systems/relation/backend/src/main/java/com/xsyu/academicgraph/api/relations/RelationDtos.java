package com.xsyu.academicgraph.api.relations;

import java.util.List;

/**
 * 关系分析板块的响应 DTO。
 *
 * 与"多维分析"（analytics：全局 TOP-N 统计）的职责不同，这里围绕一个中心实体做关系钻取：
 * 从某位作者出发看合作者、从全体作者看领域分区、从某所机构看内部作者与机构间合作。
 * 五个 record 的字段与 Cypher 的 RETURN 别名一一对应，风格沿用 AnalyticsDtos。
 */
public final class RelationDtos {

    private RelationDtos() {
    }

    /** 合作者条目：某作者的一位合作者（共同论文只带前 5 篇标题，防止高产作者把响应撑大） */
    public record CoauthorItem(
            Long authorId,
            String authorName,
            Long paperCount,
            List<String> paperTitles,
            List<String> institutions,
            boolean sameInstitution
    ) {
    }

    /** 领域分区行：某领域下的一位作者及其论文数（扁平行，前端按 field 分组聚合） */
    public record FieldPartitionItem(
            String field,
            Long authorId,
            String authorName,
            Long paperCount
    ) {
    }

    /** 作者主题画像：某作者的一个关键词及出现年份（years 数组供前端画主题迁移区间） */
    public record AuthorTopicItem(
            Long keywordId,
            String keyword,
            String fieldName,
            Long paperCount,
            List<Integer> years
    ) {
    }

    /** 机构内部作者：某机构下的一位作者及其论文数、引用热度合计 */
    public record InstitutionAuthorItem(
            Long authorId,
            String authorName,
            Long paperCount,
            Long totalCitations
    ) {
    }

    /** 机构间合作：两所机构共同署名的论文数（边权重），businessId 大小比较保证每对只出现一次 */
    public record InstitutionCollabItem(
            Long inst1Id,
            String inst1,
            Long inst2Id,
            String inst2,
            Long paperCount
    ) {
    }
}
