package com.xsyu.academicgraph.api.keywords;

import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.api.common.Paging;
import com.xsyu.academicgraph.api.keywords.KeywordDtos.KeywordResponse;
import com.xsyu.academicgraph.api.keywords.KeywordDtos.KeywordUpsertRequest;
import com.xsyu.academicgraph.application.academic.KeywordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关键词资源接口（/api/v1/keywords）：标准 CRUD。
 * 所有写接口需要登录（SecurityConfig 全局要求认证），分页走 Paging 统一契约。
 */
@RestController
@RequestMapping("/api/v1/keywords")
@RequiredArgsConstructor
public class KeywordController {

    private final KeywordService keywordService;

    /** 分页列表：keyword 按关键词名模糊搜索 */
    @GetMapping
    public PageResponse<KeywordResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return keywordService.list(keyword, Paging.of(page, size));
    }

    @GetMapping("/{id}")
    public KeywordResponse get(@PathVariable Long id) {
        return keywordService.get(id);
    }

    /** 创建：返回 201；名称重复返回 400（预检）或 409（并发下唯一索引兜底） */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KeywordResponse create(@Valid @RequestBody KeywordUpsertRequest request) {
        return keywordService.create(request);
    }

    @PutMapping("/{id}")
    public KeywordResponse update(@PathVariable Long id, @Valid @RequestBody KeywordUpsertRequest request) {
        return keywordService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        keywordService.delete(id);
    }
}
