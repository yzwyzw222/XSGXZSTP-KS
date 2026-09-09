package com.xsyu.academicgraph.api.venues;

import com.xsyu.academicgraph.api.common.PageResponse;
import com.xsyu.academicgraph.api.common.Paging;
import com.xsyu.academicgraph.api.venues.VenueDtos.VenueResponse;
import com.xsyu.academicgraph.api.venues.VenueDtos.VenueUpsertRequest;
import com.xsyu.academicgraph.application.academic.VenueService;
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
 * 发表渠道资源接口（/api/v1/venues）：标准 CRUD。
 * 所有写接口需要登录（SecurityConfig 全局要求认证），分页走 Paging 统一契约。
 */
@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    /** 分页列表：keyword 按渠道名模糊搜索 */
    @GetMapping
    public PageResponse<VenueResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return venueService.list(keyword, Paging.of(page, size));
    }

    @GetMapping("/{id}")
    public VenueResponse get(@PathVariable Long id) {
        return venueService.get(id);
    }

    /** 创建：返回 201 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse create(@Valid @RequestBody VenueUpsertRequest request) {
        return venueService.create(request);
    }

    @PutMapping("/{id}")
    public VenueResponse update(@PathVariable Long id, @Valid @RequestBody VenueUpsertRequest request) {
        return venueService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        venueService.delete(id);
    }
}
