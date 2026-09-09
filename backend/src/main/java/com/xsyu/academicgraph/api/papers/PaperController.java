package com.xsyu.academicgraph.api.papers;

import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.api.common.Paging;
import com.xsyu.academicgraph.api.papers.PaperDtos.PaperResponse;
import com.xsyu.academicgraph.api.papers.PaperDtos.PaperUpsertRequest;
import com.xsyu.academicgraph.application.academic.PaperService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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
 * 论文资源接口（/api/v1/papers）。
 * 分页参数按工程规范契约：page 从 0 开始，size 默认 20、最大 100。
 */
@RestController
@RequestMapping("/api/v1/papers")
@RequiredArgsConstructor
public class PaperController {

    private final PaperService paperService;

    /** 分页列表：keyword 按标题模糊搜索，paperType/year 精确过滤 */
    @GetMapping
    public PageResponse<PaperResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String paperType,
            @RequestParam(required = false) Short year,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return paperService.list(keyword, paperType, year, Paging.of(page, size));
    }

    @GetMapping("/{id}")
    public PaperResponse get(@PathVariable Long id) {
        return paperService.get(id);
    }

    /** 创建：返回 201。created_by（对象级所有权）二期启用，当前传 null，数据库列允许为空 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaperResponse create(@Valid @RequestBody PaperUpsertRequest request) {
        return paperService.create(request, null);
    }

    /** 更新：请求体必须带 version，与库中不一致返回 409（乐观锁） */
    @PutMapping("/{id}")
    public PaperResponse update(@PathVariable Long id, @Valid @RequestBody PaperUpsertRequest request) {
        return paperService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        paperService.delete(id);
    }
}
