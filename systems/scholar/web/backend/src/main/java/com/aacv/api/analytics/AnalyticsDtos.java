package com.aacv.api.analytics;

import java.util.List;

/**
 * 科研分析接口 DTO 集合。
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** 年份-数量。 */
    public record YearCount(Integer year, long count) {
    }

    /** 年份-角色统计：该年总论文数 / 第一作者论文数 / 通讯作者论文数。 */
    public record YearRoleCount(Integer year, long total, long firstAuthor, long corresponding) {
    }

    /** 名称-数量。 */
    public record NameCount(String name, long count) {
    }

    /** 主题演化：年份轴 + 每个主题在各年份的论文数。 */
    public record TopicEvolutionResponse(List<Integer> years, List<TopicSeries> topics) {
    }

    /** 单个主题的年度序列。 */
    public record TopicSeries(String name, List<Long> counts) {
    }

    /** 网络图：节点 + 边（关键词共现 / 合作者 / 机构合作 / 论文相似）。 */
    public record NetworkGraph(List<Node> nodes, List<Link> links) {
    }

    /** 网络节点：value 用于节点大小。 */
    public record Node(String id, String name, long value, String category) {
    }

    /** 网络边：weight 用于线宽。 */
    public record Link(String source, String target, long weight) {
    }

    /** 被引统计。 */
    public record CitationStats(long totalCitations, double avgPerPaper, long citedPapers,
                                List<NameCount> topCited) {
    }
}
