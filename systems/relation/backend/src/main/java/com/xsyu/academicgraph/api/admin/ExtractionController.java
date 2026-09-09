package com.xsyu.academicgraph.api.admin;

import com.xsyu.academicgraph.api.admin.ExtractionDtos.ExtractionResultDto;
import com.xsyu.academicgraph.api.admin.ExtractionDtos.ExtractionStatusDto;
import com.xsyu.academicgraph.api.admin.ExtractionDtos.ExtractionTriggerRequest;
import com.xsyu.academicgraph.api.admin.ExtractionDtos.ExtractionTriggerResponse;
import com.xsyu.academicgraph.application.extraction.ExtractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实体抽取管理接口（/api/v1/admin/extraction/**，ADMIN + CSRF 由 SecurityConfig 兜底）。
 * 触发是异步的：POST 立即返回 202 与任务清单，前端轮询 GET /status/{paperId} 观察状态机推进。
 */
@RestController
@RequestMapping("/api/v1/admin/extraction")
@RequiredArgsConstructor
public class ExtractionController {

    private final ExtractionService extractionService;

    /**
     * 触发抽取。请求体可省略（此时抽最早的 10 篇 PENDING 论文）；
     * paperIds 统一是数组，与前端契约一致。
     */
    @PostMapping("/trigger")
    public ResponseEntity<ExtractionTriggerResponse> trigger(
            @Valid @RequestBody(required = false) ExtractionTriggerRequest request) {
        List<Long> paperIds = request == null ? null : request.paperIds();
        List<Long> resolved = extractionService.resolveTargetPaperIds(paperIds);
        extractionService.executeAsync(resolved);
        return ResponseEntity.accepted().body(new ExtractionTriggerResponse(
                "抽取任务已进入异步队列，请轮询 /status/{paperId} 查看进度", resolved.size(), resolved));
    }

    /** 单篇抽取状态：PENDING/IN_PROGRESS/COMPLETED/FAILED + 台账计数 */
    @GetMapping("/status/{paperId}")
    public ExtractionStatusDto status(@PathVariable Long paperId) {
        return extractionService.status(paperId);
    }

    /** 单篇抽取明细：台账实体行 + 关系行（端点已回填名称） */
    @GetMapping("/result/{paperId}")
    public ExtractionResultDto result(@PathVariable Long paperId) {
        return extractionService.result(paperId);
    }
}
