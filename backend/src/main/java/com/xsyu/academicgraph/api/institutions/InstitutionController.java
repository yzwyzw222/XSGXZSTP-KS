package com.xsyu.academicgraph.api.institutions;

import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.api.common.Paging;
import com.xsyu.academicgraph.api.institutions.InstitutionDtos.InstitutionResponse;
import com.xsyu.academicgraph.api.institutions.InstitutionDtos.InstitutionUpsertRequest;
import com.xsyu.academicgraph.application.academic.InstitutionService;
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
 * 机构资源接口（/api/v1/institutions）：标准 CRUD。
 * 所有写接口需要登录（SecurityConfig 全局要求认证），分页走 Paging 统一契约。
 */
@RestController
@RequestMapping("/api/v1/institutions")
@RequiredArgsConstructor
public class InstitutionController {

    private final InstitutionService institutionService;

    /** 分页列表：keyword 按机构名模糊搜索 */
    @GetMapping
    public PageResponse<InstitutionResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return institutionService.list(keyword, Paging.of(page, size));
    }

    @GetMapping("/{id}")
    public InstitutionResponse get(@PathVariable Long id) {
        return institutionService.get(id);
    }

    /** 创建：返回 201 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstitutionResponse create(@Valid @RequestBody InstitutionUpsertRequest request) {
        return institutionService.create(request);
    }

    /** 更新：请求体必须带 version，与库中不一致返回 409（乐观锁） */
    @PutMapping("/{id}")
    public InstitutionResponse update(@PathVariable Long id, @Valid @RequestBody InstitutionUpsertRequest request) {
        return institutionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        institutionService.delete(id);
    }
}
