package com.xsyu.academicgraph.api.analytics;

import com.xsyu.academicgraph.api.analytics.AnalyticsDtos.CitationItem;
import com.xsyu.academicgraph.api.analytics.AnalyticsDtos.CollaborationItem;
import com.xsyu.academicgraph.api.analytics.AnalyticsDtos.InstitutionImpactItem;
import com.xsyu.academicgraph.api.analytics.AnalyticsDtos.TopicEvolutionItem;
import com.xsyu.academicgraph.application.analytics.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 图谱分析接口（/api/v1/analytics）：大创项目四大分析方向。
 * 全部是只读查询，直接返回数组（不套分页包装），limit 控制返回条数。
 * 需要登录（SecurityConfig 全局要求认证），管理员与普通用户均可访问。
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** 合作网络 TOP-N：按合著论文数降序 */
    @GetMapping("/collaborations")
    public List<CollaborationItem> collaborations(@RequestParam(required = false) Integer limit) {
        return analyticsService.collaborations(limit);
    }

    /**
     * 合作关系边：图谱页"显示合作关系"开关的数据源。
     * 读的是 Neo4j 里物化好的 COAUTHOR_WITH 派生边（weight = 共同署名论文数），
     * 前端把它画成作者—作者的直连边，线宽随 weight 变化。
     */
    @GetMapping("/coauthor-edges")
    public List<CollaborationItem> coauthorEdges(@RequestParam(required = false) Integer limit) {
        return analyticsService.coauthorEdges(limit);
    }

    /** 引用统计 TOP-N：按图内被引次数降序 */
    @GetMapping("/citations")
    public List<CitationItem> citations(@RequestParam(required = false) Integer limit) {
        return analyticsService.citations(limit);
    }

    /** 主题演化：年份 × 关键词 频次明细（按年份升序） */
    @GetMapping("/topic-evolution")
    public List<TopicEvolutionItem> topicEvolution(@RequestParam(required = false) Integer limit) {
        return analyticsService.topicEvolution(limit);
    }

    /** 机构影响力 TOP-N：按署名论文引用热度合计降序 */
    @GetMapping("/institution-impact")
    public List<InstitutionImpactItem> institutionImpact(@RequestParam(required = false) Integer limit) {
        return analyticsService.institutionImpact(limit);
    }
}
