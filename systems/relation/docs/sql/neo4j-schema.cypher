// =====================================================================
// 学术关系知识图谱构建平台 — Neo4j 4.4 图模式初始化
// 说明：Neo4j 为可重建图投影；businessId = MySQL 对应表主键，是幂等 MERGE 的锚点
// 用法：在 Neo4j Browser（http://localhost:7474）逐条或整段执行
// =====================================================================

// ---------- 节点唯一约束（幂等投影锚点） ----------
CREATE CONSTRAINT kg_paper_business_id IF NOT EXISTS FOR (n:Paper) REQUIRE n.businessId IS UNIQUE;
CREATE CONSTRAINT kg_author_business_id IF NOT EXISTS FOR (n:Author) REQUIRE n.businessId IS UNIQUE;
CREATE CONSTRAINT kg_institution_business_id IF NOT EXISTS FOR (n:Institution) REQUIRE n.businessId IS UNIQUE;
CREATE CONSTRAINT kg_venue_business_id IF NOT EXISTS FOR (n:Venue) REQUIRE n.businessId IS UNIQUE;
CREATE CONSTRAINT kg_keyword_business_id IF NOT EXISTS FOR (n:Keyword) REQUIRE n.businessId IS UNIQUE;
CREATE CONSTRAINT kg_research_entity_business_id IF NOT EXISTS FOR (n:ResearchEntity) REQUIRE n.businessId IS UNIQUE;

// ---------- 查询索引 ----------
CREATE INDEX kg_paper_publication_year IF NOT EXISTS FOR (n:Paper) ON (n.publicationYear);
CREATE INDEX kg_paper_doi IF NOT EXISTS FOR (n:Paper) ON (n.doi);
CREATE INDEX kg_paper_type IF NOT EXISTS FOR (n:Paper) ON (n.paperType);
CREATE INDEX kg_author_name IF NOT EXISTS FOR (n:Author) ON (n.name);
CREATE INDEX kg_keyword_name IF NOT EXISTS FOR (n:Keyword) ON (n.name);
CREATE INDEX kg_institution_name IF NOT EXISTS FOR (n:Institution) ON (n.name);
CREATE INDEX kg_research_entity_name IF NOT EXISTS FOR (n:ResearchEntity) ON (n.name);

// =====================================================================
// 图模式说明（节点属性 / 关系）
// (:Paper)       {businessId,title,doi,paperType,publicationDate,publicationYear,citationCount}
// (:Author)      {businessId,name,orcid}
// (:Institution) {businessId,name,countryCode}
// (:Venue)       {businessId,name,venueType,issn}
// (:Keyword)     {businessId,name,fieldName}
//
// (:Author)-[:AUTHORED {position}]->(:Paper)
// (:Author)-[:AFFILIATED_WITH]->(:Institution)
// (:Paper)-[:PUBLISHED_IN]->(:Venue)
// (:Paper)-[:HAS_KEYWORD]->(:Keyword)
// (:Paper)-[:CITES]->(:Paper)
//
// LLM 实体抽取投影（边都带 paperId 属性，重投影时按 paperId 清理重建）：
// (:ResearchEntity) {businessId,name,entityType}
// (:ResearchEntity)-[:EXTRACTED_FROM {paperId}]->(:Paper)
// (:Institution)-[:EXTRACTED_FROM {paperId}]->(:Paper)
// 实体间 LLM 关系边（六种，带 {paperId,evidence,confidence}）：
// USES / EXTENDS / EVALUATES_ON / APPLIED_TO / PROPOSED_BY / COMPARED_WITH
// =====================================================================
