package com.aacv.api.institution;

import com.aacv.api.common.PageResponse;
import com.aacv.api.keyword.KeywordDtos.PaperRow;
import com.aacv.application.institution.InstitutionSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 机构检索接口：机构列表 + 机构参与的论文分页列表。
 */
@RestController
@RequestMapping("/api/v1/institutions")
public class InstitutionSearchController {

    private final InstitutionSearchService institutionSearchService;

    public InstitutionSearchController(InstitutionSearchService institutionSearchService) {
        this.institutionSearchService = institutionSearchService;
    }

    /** 机构列表，search 可选（名称模糊匹配）。 */
    @GetMapping
    public List<InstitutionDtos.InstitutionItem> institutions(@RequestParam(required = false) String search) {
        return institutionSearchService.list(search);
    }

    /** 某机构参与的论文分页列表（page 从 1 开始）。 */
    @GetMapping("/{instId}/papers")
    public PageResponse<PaperRow> papers(@PathVariable String instId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return institutionSearchService.papers(instId, page, size);
    }
}
