package com.aacv.api.aiselection;

import com.aacv.api.aiselection.AiSelectionDtos.SourceItem;
import com.aacv.api.common.PageResponse;
import com.aacv.api.keyword.KeywordDtos.PaperRow;
import com.aacv.application.aiselection.AiSelectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 选文分析接口：数据来源列表 + 来源内论文搜索（作者/关键词/标题）。
 */
@RestController
@RequestMapping("/api/v1/ai-selection")
public class AiSelectionController {

    private final AiSelectionService aiSelectionService;

    public AiSelectionController(AiSelectionService aiSelectionService) {
        this.aiSelectionService = aiSelectionService;
    }

    /** 数据来源列表（来源库 + 论文数）。 */
    @GetMapping("/sources")
    public List<SourceItem> sources() {
        return aiSelectionService.sources();
    }

    /** 来源内论文搜索（search 匹配作者名、关键词或标题），page 从 1 开始。 */
    @GetMapping("/papers")
    public PageResponse<PaperRow> papers(@RequestParam(required = false) String source,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return aiSelectionService.search(source, search, page, size);
    }
}
