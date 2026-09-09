package com.xsyu.academicgraph.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 在线爬取（OpenAlex）的请求 DTO（管理员功能）。
 * 前端只提交关键词与期望条数，服务端负责搜索、映射、落库，返回与文件导入相同的汇总结构。
 */
public final class CrawlDtos {

    private CrawlDtos() {
    }

    /** 爬取请求：关键词 + 条数（1~25，缺省走服务端配置的单页条数） */
    public record CrawlRequest(
            @NotBlank(message = "搜索关键词不能为空")
            @Size(max = 200, message = "搜索关键词最长 200 字符")
            String keyword,
            @Min(value = 1, message = "爬取条数至少 1 条")
            @Max(value = 25, message = "单次最多爬取 25 条（OpenAlex 单页上限）")
            Integer maxRecords
    ) {
    }
}
