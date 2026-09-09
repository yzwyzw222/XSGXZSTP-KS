package com.aacv.api.keyword;

import com.aacv.api.common.PageResponse;
import com.aacv.api.keyword.KeywordDtos.KeywordItem;
import com.aacv.api.keyword.KeywordDtos.PaperRow;
import com.aacv.application.keyword.KeywordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 关键词检索接口：关键词列表 + 关键词下的论文分页列表。
 */
@RestController
@RequestMapping("/api/v1/keywords")
public class KeywordController {

    private final KeywordService keywordService;

    public KeywordController(KeywordService keywordService) {
        this.keywordService = keywordService;
    }

    /** 关键词列表，search 可选（名称模糊匹配）。 */
    @GetMapping
    public List<KeywordItem> keywords(@RequestParam(required = false) String search) {
        return keywordService.keywords(search);
    }

    /** 某个关键词下的论文分页列表（page 从 1 开始）。 */
    @GetMapping("/{topicId}/papers")
    public PageResponse<PaperRow> papers(@PathVariable String topicId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return keywordService.papers(topicId, page, size);
    }
}
