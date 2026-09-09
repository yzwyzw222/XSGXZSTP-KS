package com.xsyu.academicgraph.api.extraction;

import java.util.List;

/**
 * 抽取图谱数据 DTO（GET /api/v1/extraction/graph-data，登录即可）。
 * 与 GraphView 主图谱数据同构（节点/边），叠加展示：
 *  - extracted 边 = EXTRACTED_FROM：抽取实体 → 来源论文（paper_* 节点）
 *  - llm 边 = 六类 LLM 关系（USES/EXTENDS/...），端点都是已归并的业务实体节点
 */
public class ExtractionGraphDtos {

    public record ExtractionGraphData(
            List<ExtractionGraphNode> nodes,
            List<ExtractionGraphEdge> edges) {
    }

    /**
     * 节点 id 与 GraphView 既有命名规则一致：author_3 / institution_5 / keyword_7 / research_9。
     * type 是前端样式映射键（research/author/institution/keyword）；
     * entityType 仅研究实体节点有值（METHOD/DATASET/TOOL），用于区分方法/数据集/工具。
     */
    public record ExtractionGraphNode(
            String id,
            String label,
            String type,
            String entityType) {
    }

    /**
     * kind: extracted = EXTRACTED_FROM 证据边；llm = LLM 抽取的关系边。
     * label: extracted 边固定 EXTRACTED_FROM；llm 边是关系类型名。
     */
    public record ExtractionGraphEdge(
            String id,
            String source,
            String target,
            String kind,
            String label,
            String evidence,
            Double confidence) {
    }
}
