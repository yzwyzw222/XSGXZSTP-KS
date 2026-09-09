package com.xsyu.academicgraph.api.analytics;

/**
 * 图谱分析接口的响应 DTO。
 * 四个维度各一条 record，字段与 Neo4j Cypher 的 RETURN 别名一一对应，
 * 对应大创项目的四大分析方向：合作网络 / 引用分析 / 主题演化 / 机构影响力。
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** 合作网络边：两位作者共同署名过的论文数（边权重），用于前端画作者合作图 */
    public record CollaborationItem(
            Long author1Id,
            String author1,
            Long author2Id,
            String author2,
            Long paperCount
    ) {
    }

    /** 引用统计：论文的施引数（citesOut）与图内被引数（citesIn），按被引数降序 */
    public record CitationItem(
            Long paperId,
            String title,
            Long citesOut,
            Long citesIn
    ) {
    }

    /** 主题演化点：某年份某关键词的出现频次，前端按年份分组画折线/热力图 */
    public record TopicEvolutionItem(
            Integer year,
            String keyword,
            Long paperCount
    ) {
    }

    /** 机构影响力：机构署名论文数与这些论文引用热度（citationCount）的合计 */
    public record InstitutionImpactItem(
            String institution,
            Long paperCount,
            Long totalCitations
    ) {
    }
}
