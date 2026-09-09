package com.aacv.api.paper;

import com.aacv.application.paper.PaperSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 论文检索接口：论文搜索列表 + 某篇论文的主题列表。
 */
@RestController
@RequestMapping("/api/v1/papers")
public class PaperSearchController {

    private final PaperSearchService paperSearchService;

    public PaperSearchController(PaperSearchService paperSearchService) {
        this.paperSearchService = paperSearchService;
    }

    /** 论文列表，search 可选（标题模糊匹配）。 */
    @GetMapping
    public List<PaperDtos.PaperItem> papers(@RequestParam(required = false) String search) {
        return paperSearchService.list(search);
    }

    /** 某篇论文包含的研究主题。 */
    @GetMapping("/{paperId}/topics")
    public List<PaperDtos.TopicItem> topics(@PathVariable String paperId) {
        return paperSearchService.topics(paperId);
    }
}
