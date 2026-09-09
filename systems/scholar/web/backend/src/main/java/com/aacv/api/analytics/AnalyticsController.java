package com.aacv.api.analytics;

import com.aacv.api.analytics.AnalyticsDtos.CitationStats;
import com.aacv.api.analytics.AnalyticsDtos.NameCount;
import com.aacv.api.analytics.AnalyticsDtos.NetworkGraph;
import com.aacv.api.analytics.AnalyticsDtos.TopicEvolutionResponse;
import com.aacv.api.analytics.AnalyticsDtos.YearCount;
import com.aacv.api.analytics.AnalyticsDtos.YearRoleCount;
import com.aacv.application.analytics.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 科研分析接口：趋势、演化、网络、分布、被引。
 * 全部接口支持可选 authorId 参数：传入时仅统计该学者的数据。
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** 1. 论文发表时间趋势。 */
    @GetMapping("/publication-trend")
    public List<YearCount> publicationTrend(@RequestParam(required = false) String authorId) {
        return analyticsService.publicationTrend(authorId);
    }

    /** 1b. 年份-作者角色统计（总产出/一作/通讯）。 */
    @GetMapping("/author-role-trend")
    public List<YearRoleCount> authorRoleTrend(@RequestParam(required = false) String authorId) {
        return analyticsService.authorRoleTrend(authorId);
    }

    /** 1c. 研究方向（主题）分布。 */
    @GetMapping("/topic-distribution")
    public List<NameCount> topicDistribution(@RequestParam(defaultValue = "10") int topN,
                                             @RequestParam(required = false) String authorId) {
        return analyticsService.topicDistribution(topN, authorId);
    }

    /** 2. 研究主题演化（Top N 主题 × 年份）。 */
    @GetMapping("/topic-evolution")
    public TopicEvolutionResponse topicEvolution(@RequestParam(defaultValue = "8") int topN,
                                                 @RequestParam(required = false) String authorId) {
        return analyticsService.topicEvolution(topN, authorId);
    }

    /** 3. 关键词共现网络。 */
    @GetMapping("/keyword-cooccurrence")
    public NetworkGraph keywordCooccurrence(
            @RequestParam(defaultValue = "30") int topN,
            @RequestParam(defaultValue = "2") long minWeight,
            @RequestParam(required = false) String authorId) {
        return analyticsService.keywordCooccurrence(topN, minWeight, authorId);
    }

    /** 4. 论文相似网络（共享主题数 >= minShared）。 */
    @GetMapping("/paper-similarity")
    public NetworkGraph paperSimilarity(
            @RequestParam(defaultValue = "40") int topN,
            @RequestParam(defaultValue = "2") long minShared,
            @RequestParam(required = false) String authorId) {
        return analyticsService.paperSimilarityNetwork(topN, minShared, authorId);
    }

    /** 5. 合作作者网络。 */
    @GetMapping("/coauthor-network")
    public NetworkGraph coauthorNetwork(
            @RequestParam(defaultValue = "30") int topN,
            @RequestParam(defaultValue = "1") long minWeight,
            @RequestParam(required = false) String authorId) {
        return analyticsService.coauthorNetwork(topN, minWeight, authorId);
    }

    /** 6. 机构合作网络（基于论文署名机构解析）。 */
    @GetMapping("/institution-network")
    public NetworkGraph institutionNetwork(
            @RequestParam(defaultValue = "20") int topN,
            @RequestParam(defaultValue = "2") long minWeight,
            @RequestParam(required = false) String authorId) {
        return analyticsService.institutionNetwork(topN, minWeight, authorId);
    }

    /** 7. 期刊分布（Top N）。 */
    @GetMapping("/venue-distribution")
    public List<NameCount> venueDistribution(@RequestParam(defaultValue = "15") int topN,
                                             @RequestParam(required = false) String authorId) {
        return analyticsService.venueDistribution(topN, authorId);
    }

    /** 8. 被引统计。 */
    @GetMapping("/citation-stats")
    public CitationStats citationStats(@RequestParam(defaultValue = "10") int topN,
                                       @RequestParam(required = false) String authorId) {
        return analyticsService.citationStats(topN, authorId);
    }

    /** 9. 论文类型分布。 */
    @GetMapping("/paper-type-distribution")
    public List<NameCount> paperTypeDistribution(@RequestParam(required = false) String authorId) {
        return analyticsService.paperTypeDistribution(authorId);
    }
}
