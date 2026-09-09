package com.aacv.api.author;

import com.aacv.api.author.AuthorDtos.AuthorItem;
import com.aacv.api.common.PageResponse;
import com.aacv.api.keyword.KeywordDtos.PaperRow;
import com.aacv.application.author.AuthorSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者检索接口：作者列表 + 某作者名下的论文分页列表。
 */
@RestController
@RequestMapping("/api/v1/authors")
public class AuthorSearchController {

    private final AuthorSearchService authorSearchService;

    public AuthorSearchController(AuthorSearchService authorSearchService) {
        this.authorSearchService = authorSearchService;
    }

    /** 作者列表，search 可选（姓名模糊匹配）。 */
    @GetMapping
    public List<AuthorItem> authors(@RequestParam(required = false) String search) {
        return authorSearchService.authorList(search);
    }

    /** 某作者名下的论文分页列表（page 从 1 开始）。 */
    @GetMapping("/{authorId}/papers")
    public PageResponse<PaperRow> papers(@PathVariable String authorId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return authorSearchService.papers(authorId, page, size);
    }
}
