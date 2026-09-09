package com.xsyu.academicgraph.api.admin;

import com.xsyu.academicgraph.api.admin.CrawlDtos.CrawlRequest;
import com.xsyu.academicgraph.api.admin.DataImportDtos.ImportSummary;
import com.xsyu.academicgraph.application.crawl.CrawlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线爬取接口（POST /api/v1/admin/crawl/openalex）。
 * SecurityConfig 已限定 /api/v1/admin/** 需要 ADMIN 角色 + CSRF，这里零安全配置。
 * 同步接口：一次请求抓一页（≤25 条）并直接落库，返回与文件导入同构的汇总结果。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class CrawlController {

    private final CrawlService crawlService;

    /** 按关键词在线爬取 OpenAlex 论文：搜索 → 解析 → 映射 → 落库 → 汇总 */
    @PostMapping("/crawl/openalex")
    public ImportSummary crawlOpenAlex(@Valid @RequestBody CrawlRequest request) {
        return crawlService.crawl(request.keyword(), request.maxRecords());
    }
}
