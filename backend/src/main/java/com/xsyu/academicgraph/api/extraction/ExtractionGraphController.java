package com.xsyu.academicgraph.api.extraction;

import com.xsyu.academicgraph.api.extraction.ExtractionGraphDtos.ExtractionGraphData;
import com.xsyu.academicgraph.application.extraction.ExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 抽取图谱数据接口（/api/v1/extraction/**，SecurityConfig 的 anyRequest().authenticated()
 * 已要求登录——这是只读聚合，不需要 ADMIN）。
 * 前端 GraphView 叠加展示：研究实体节点 + EXTRACTED_FROM 证据边 + LLM 关系边。
 */
@RestController
@RequestMapping("/api/v1/extraction")
@RequiredArgsConstructor
public class ExtractionGraphController {

    private final ExtractionService extractionService;

    /** 取抽取图谱数据；paperIds 可选（缺省取全部已抽取论文） */
    @GetMapping("/graph-data")
    public ExtractionGraphData graphData(@RequestParam(required = false) List<Long> paperIds) {
        return extractionService.graphData(paperIds);
    }
}
