package com.xsyu.academicgraph.application.analytics;

import com.xsyu.academicgraph.api.analytics.AnalyticsDtos.CitationItem;
import com.xsyu.academicgraph.api.analytics.AnalyticsDtos.CollaborationItem;
import com.xsyu.academicgraph.api.analytics.AnalyticsDtos.InstitutionImpactItem;
import com.xsyu.academicgraph.api.analytics.AnalyticsDtos.TopicEvolutionItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.xsyu.academicgraph.application.analytics.Neo4jValues.normalizeLimit;
import static com.xsyu.academicgraph.application.analytics.Neo4jValues.toInt;
import static com.xsyu.academicgraph.application.analytics.Neo4jValues.toLong;
import static com.xsyu.academicgraph.application.analytics.Neo4jValues.toStr;

/**
 * 图谱分析服务：四大分析维度的 Cypher 查询全部落在 Neo4j（图投影）上。
 * MySQL 存关系行，但"多跳关系聚合"（共同作者、被引计数、按年统计）正是图数据库的主场：
 * 同样的查询用 SQL 要多次 JOIN，用 Cypher 一条路径匹配即可。
 * 查询结果通过 Neo4jClient 的 mappedBy 把 Cypher 记录映射成强类型 DTO。
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final Neo4jClient neo4jClient;

    /**
     * 合作网络 TOP-N：两两作者共同署名的论文数。
     * businessId 大小比较（<）保证每对作者只出现一次，避免 (A,B) 与 (B,A) 重复计数。
     */
    public List<CollaborationItem> collaborations(Integer limit) {
        int n = normalizeLimit(limit, 50, 200);
        return neo4jClient.query("""
                        MATCH (a:Author)-[:AUTHORED]->(p:Paper)<-[:AUTHORED]-(b:Author)
                        WHERE a.businessId < b.businessId
                        RETURN a.businessId AS author1Id, a.name AS author1,
                               b.businessId AS author2Id, b.name AS author2,
                               count(p) AS paperCount
                        ORDER BY paperCount DESC
                        LIMIT $limit
                        """)
                .bind(n).to("limit")
                .fetchAs(CollaborationItem.class)
                .mappedBy((typeSystem, record) -> new CollaborationItem(
                        toLong(record.get("author1Id")),
                        toStr(record.get("author1")),
                        toLong(record.get("author2Id")),
                        toStr(record.get("author2")),
                        toLong(record.get("paperCount"))))
                .all().stream().toList();
    }

    /**
     * 合作关系边（读物化边 COAUTHOR_WITH）：图谱页"显示合作关系"开关的数据源。
     *
     * 与 collaborations() 的区别：
     *   collaborations() 每次都在 AUTHORED 上实时做作者自连接聚合，结果永远最新但要算；
     *   本方法读的是 GraphSyncService.recomputeCoauthorEdges() 物化好的派生边，一跳即得，
     *   边上还带 computedAt 记录物化时间（派生边落后于事实边是图投影的正常状态）。
     * 复用 CollaborationItem：字段形状完全一致，paperCount 就是边上的 weight（共同署名论文数）。
     */
    public List<CollaborationItem> coauthorEdges(Integer limit) {
        int n = normalizeLimit(limit, 200, 1000);
        return neo4jClient.query("""
                        MATCH (a:Author)-[r:COAUTHOR_WITH]->(b:Author)
                        RETURN a.businessId AS author1Id, a.name AS author1,
                               b.businessId AS author2Id, b.name AS author2,
                               r.weight AS paperCount
                        ORDER BY paperCount DESC, author1Id ASC
                        LIMIT $limit
                        """)
                .bind(n).to("limit")
                .fetchAs(CollaborationItem.class)
                .mappedBy((typeSystem, record) -> new CollaborationItem(
                        toLong(record.get("author1Id")),
                        toStr(record.get("author1")),
                        toLong(record.get("author2Id")),
                        toStr(record.get("author2")),
                        toLong(record.get("paperCount"))))
                .all().stream().toList();
    }

    /** 引用统计 TOP-N：按图内被引次数降序，施引数作参考（citationCount 是 MySQL 里的外部引用热度） */
    public List<CitationItem> citations(Integer limit) {
        int n = normalizeLimit(limit, 50, 200);
        return neo4jClient.query("""
                        MATCH (p:Paper)
                        OPTIONAL MATCH (p)-[:CITES]->(cited:Paper)
                        OPTIONAL MATCH (citer:Paper)-[:CITES]->(p)
                        RETURN p.businessId AS paperId, p.title AS title,
                               count(DISTINCT cited) AS citesOut,
                               count(DISTINCT citer) AS citesIn
                        ORDER BY citesIn DESC, citesOut DESC
                        LIMIT $limit
                        """)
                .bind(n).to("limit")
                .fetchAs(CitationItem.class)
                .mappedBy((typeSystem, record) -> new CitationItem(
                        toLong(record.get("paperId")),
                        toStr(record.get("title")),
                        toLong(record.get("citesOut")),
                        toLong(record.get("citesIn"))))
                .all().stream().toList();
    }

    /**
     * 主题演化：每个年份 × 关键词的出现频次。
     * 返回按"年份升序、频次降序"排序的明细行，前端按年份分组渲染演化曲线。
     */
    public List<TopicEvolutionItem> topicEvolution(Integer limit) {
        int n = normalizeLimit(limit, 200, 1000);
        return neo4jClient.query("""
                        MATCH (p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                        WHERE p.publicationYear IS NOT NULL
                        RETURN p.publicationYear AS year, k.name AS keyword, count(p) AS paperCount
                        ORDER BY year ASC, paperCount DESC
                        LIMIT $limit
                        """)
                .bind(n).to("limit")
                .fetchAs(TopicEvolutionItem.class)
                .mappedBy((typeSystem, record) -> new TopicEvolutionItem(
                        toInt(record.get("year")),
                        toStr(record.get("keyword")),
                        toLong(record.get("paperCount"))))
                .all().stream().toList();
    }

    /**
     * 机构影响力 TOP-N：机构所有署名论文的引用热度合计。
     * 注意去重：一篇论文可能有多位作者同属一个机构（匹配出多条路径），
     * 先用 collect(DISTINCT p) 收拢论文集合，再求和——每篇论文对同一机构只计一次。
     */
    public List<InstitutionImpactItem> institutionImpact(Integer limit) {
        int n = normalizeLimit(limit, 50, 200);
        return neo4jClient.query("""
                        MATCH (i:Institution)<-[:AFFILIATED_WITH]-(a:Author)-[:AUTHORED]->(p:Paper)
                        WITH i, collect(DISTINCT p) AS papers
                        RETURN i.name AS institution,
                               size(papers) AS paperCount,
                               reduce(s = 0, p IN papers | s + p.citationCount) AS totalCitations
                        ORDER BY totalCitations DESC
                        LIMIT $limit
                        """)
                .bind(n).to("limit")
                .fetchAs(InstitutionImpactItem.class)
                .mappedBy((typeSystem, record) -> new InstitutionImpactItem(
                        toStr(record.get("institution")),
                        toLong(record.get("paperCount")),
                        toLong(record.get("totalCitations"))))
                .all().stream().toList();
    }
}
