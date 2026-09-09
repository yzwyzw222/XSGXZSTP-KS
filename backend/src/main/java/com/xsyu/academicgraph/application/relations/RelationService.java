package com.xsyu.academicgraph.application.relations;

import com.xsyu.academicgraph.api.relations.RelationDtos.AuthorTopicItem;
import com.xsyu.academicgraph.api.relations.RelationDtos.CoauthorItem;
import com.xsyu.academicgraph.api.relations.RelationDtos.FieldPartitionItem;
import com.xsyu.academicgraph.api.relations.RelationDtos.InstitutionAuthorItem;
import com.xsyu.academicgraph.api.relations.RelationDtos.InstitutionCollabItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

import static com.xsyu.academicgraph.application.analytics.Neo4jValues.normalizeLimit;
import static com.xsyu.academicgraph.application.analytics.Neo4jValues.toIntList;
import static com.xsyu.academicgraph.application.analytics.Neo4jValues.toLong;
import static com.xsyu.academicgraph.application.analytics.Neo4jValues.toStr;
import static com.xsyu.academicgraph.application.analytics.Neo4jValues.toStrList;

/**
 * 关系分析服务：五个板块的关系钻取查询，全部落在 Neo4j（图投影）上。
 *
 * 为什么放图库而不是 MySQL：这些查询的共同点是"从中心实体出发沿关系多跳"——
 * 合作者（AUTHORED 两跳）、领域分区（AUTHORED+HAS_KEYWORD）、机构内部作者（AFFILIATED_WITH+AUTHORED）、
 * 机构间合作（四段路径匹配）。用 SQL 表达要多次 JOIN 加去重，用 Cypher 一条路径匹配即得。
 * 推断类关系（作者↔作者、机构↔机构）不落 MySQL，实时从事实边算出来。
 */
@Service
@RequiredArgsConstructor
public class RelationService {

    private final Neo4jClient neo4jClient;

    /**
     * 某作者的合作者列表（合作者网络板块的核心数据）。
     *
     * 查询思路：作者的论文集合 → 论文的其他署名作者，就是合作者；
     * OPTIONAL MATCH 拉双方机构集合，sameInstitution 在 Java 侧算（Cypher 里做集合交集不如 Java 直观）。
     * papers[0..5] 截断共同论文标题，防止高产作者把单条响应撑得过大。
     */
    public List<CoauthorItem> coauthors(Long authorId, Integer limit) {
        int n = normalizeLimit(limit, 50, 200);
        return neo4jClient.query("""
                        MATCH (a:Author {businessId: $authorId})-[:AUTHORED]->(p:Paper)<-[:AUTHORED]-(b:Author)
                        WHERE b.businessId <> $authorId
                        OPTIONAL MATCH (b)-[:AFFILIATED_WITH]->(bi:Institution)
                        OPTIONAL MATCH (a)-[:AFFILIATED_WITH]->(ai:Institution)
                        WITH b, collect(DISTINCT p) AS papers,
                             collect(DISTINCT bi.name) AS bInsts, collect(DISTINCT ai.name) AS aInsts
                        RETURN b.businessId AS authorId, b.name AS authorName, size(papers) AS paperCount,
                               [p IN papers[0..5] | p.title] AS paperTitles,
                               bInsts AS institutions, aInsts AS myInstitutions
                        ORDER BY paperCount DESC, authorId ASC
                        LIMIT $limit
                        """)
                .bind(authorId).to("authorId")
                .bind(n).to("limit")
                .fetchAs(CoauthorItem.class)
                .mappedBy((typeSystem, record) -> {
                    List<String> theirs = toStrList(record.get("institutions"));
                    List<String> mine = toStrList(record.get("myInstitutions"));
                    // 双方机构有交集即视为同机构合作；disjoint 为 true 表示无交集
                    boolean same = !Collections.disjoint(mine, theirs);
                    return new CoauthorItem(
                            toLong(record.get("authorId")),
                            toStr(record.get("authorName")),
                            toLong(record.get("paperCount")),
                            toStrList(record.get("paperTitles")),
                            theirs,
                            same);
                })
                .all().stream().toList();
    }

    /**
     * 全体作者的领域分区（研究领域分区板块的总览视图）。
     *
     * 返回扁平行（领域 × 作者 × 论文数）而不是嵌套结构：mappedBy 拆嵌套 Map 要写递归拆包，
     * 扁平化后前端 Object.groupBy 一行就能按领域分组，代价是行数变多——用 limit 封顶兜底。
     */
    public List<FieldPartitionItem> fieldPartition(Integer limit) {
        int n = normalizeLimit(limit, 500, 2000);
        return neo4jClient.query("""
                        MATCH (a:Author)-[:AUTHORED]->(p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                        RETURN coalesce(k.fieldName, '未分类') AS field,
                               a.businessId AS authorId, a.name AS authorName,
                               count(DISTINCT p) AS paperCount
                        ORDER BY field ASC, paperCount DESC
                        LIMIT $limit
                        """)
                .bind(n).to("limit")
                .fetchAs(FieldPartitionItem.class)
                .mappedBy((typeSystem, record) -> new FieldPartitionItem(
                        toStr(record.get("field")),
                        toLong(record.get("authorId")),
                        toStr(record.get("authorName")),
                        toLong(record.get("paperCount"))))
                .all().stream().toList();
    }

    /**
     * 某作者的研究主题画像（研究领域分区板块的钻取视图）。
     *
     * years 数组（该关键词出现的所有年份）让前端能画"主题迁移"：最早/最晚年份之间的区间，
     * 不必再开一个接口。publicationYear 为 null 的论文（无日期）会被过滤掉。
     */
    public List<AuthorTopicItem> authorTopics(Long authorId, Integer limit) {
        int n = normalizeLimit(limit, 50, 200);
        return neo4jClient.query("""
                        MATCH (a:Author {businessId: $authorId})-[:AUTHORED]->(p:Paper)-[:HAS_KEYWORD]->(k:Keyword)
                        RETURN k.businessId AS keywordId, k.name AS keyword, k.fieldName AS fieldName,
                               count(DISTINCT p) AS paperCount,
                               collect(DISTINCT p.publicationYear) AS years
                        ORDER BY paperCount DESC
                        LIMIT $limit
                        """)
                .bind(authorId).to("authorId")
                .bind(n).to("limit")
                .fetchAs(AuthorTopicItem.class)
                .mappedBy((typeSystem, record) -> new AuthorTopicItem(
                        toLong(record.get("keywordId")),
                        toStr(record.get("keyword")),
                        toStr(record.get("fieldName")),
                        toLong(record.get("paperCount")),
                        toIntList(record.get("years"))))
                .all().stream().toList();
    }

    /**
     * 某机构下的作者（科研机构板块的"机构内部"视图）。
     *
     * 必须先用 WITH a, collect(DISTINCT p) 收拢同一作者的论文集合再 reduce 求和：
     * 一位作者有多篇论文时会匹配出多条路径，不先 DISTINCT 收拢，引用热度会被重复累加
     * （analytics 的 institutionImpact 在 L131 注释里踩过同一个坑）。
     */
    public List<InstitutionAuthorItem> institutionAuthors(Long institutionId, Integer limit) {
        int n = normalizeLimit(limit, 50, 200);
        return neo4jClient.query("""
                        MATCH (i:Institution {businessId: $institutionId})<-[:AFFILIATED_WITH]-(a:Author)-[:AUTHORED]->(p:Paper)
                        WITH a, collect(DISTINCT p) AS papers
                        RETURN a.businessId AS authorId, a.name AS authorName, size(papers) AS paperCount,
                               reduce(s = 0, p IN papers | s + p.citationCount) AS totalCitations
                        ORDER BY paperCount DESC
                        LIMIT $limit
                        """)
                .bind(institutionId).to("institutionId")
                .bind(n).to("limit")
                .fetchAs(InstitutionAuthorItem.class)
                .mappedBy((typeSystem, record) -> new InstitutionAuthorItem(
                        toLong(record.get("authorId")),
                        toStr(record.get("authorName")),
                        toLong(record.get("paperCount")),
                        toLong(record.get("totalCitations"))))
                .all().stream().toList();
    }

    /**
     * 机构间合作（科研机构板块的核心数据）：两所机构共同署名的论文数。
     *
     * i1.businessId < i2.businessId 保证每对机构只出现一次（沿用 collaborations 的去重技巧），
     * 同时天然排除了同机构内部合作（i1 = i2 不满足 <）。
     * 这是四段路径匹配的笛卡尔展开，数据量上千后建议物化成带权边（本阶段规模小，实时算即可）。
     */
    public List<InstitutionCollabItem> institutionCollaborations(Integer limit) {
        int n = normalizeLimit(limit, 50, 200);
        return neo4jClient.query("""
                        MATCH (a1:Author)-[:AUTHORED]->(p:Paper)<-[:AUTHORED]-(a2:Author),
                              (a1)-[:AFFILIATED_WITH]->(i1:Institution),
                              (a2)-[:AFFILIATED_WITH]->(i2:Institution)
                        WHERE i1.businessId < i2.businessId
                        RETURN i1.businessId AS inst1Id, i1.name AS inst1,
                               i2.businessId AS inst2Id, i2.name AS inst2,
                               count(DISTINCT p) AS paperCount
                        ORDER BY paperCount DESC
                        LIMIT $limit
                        """)
                .bind(n).to("limit")
                .fetchAs(InstitutionCollabItem.class)
                .mappedBy((typeSystem, record) -> new InstitutionCollabItem(
                        toLong(record.get("inst1Id")),
                        toStr(record.get("inst1")),
                        toLong(record.get("inst2Id")),
                        toStr(record.get("inst2")),
                        toLong(record.get("paperCount"))))
                .all().stream().toList();
    }
}
