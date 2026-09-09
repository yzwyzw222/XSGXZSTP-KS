package com.xsyu.academicgraph.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 实体抽取管理接口 DTO（POST /api/v1/admin/extraction/trigger + 状态/明细查询）。
 * 触发请求统一用 paperIds 数组——Du 前端传 {paperId: x} 的 bug 在这里从契约上杜绝；
 * paperIds 缺省时后端取最早的 10 篇 PENDING 论文。
 */
public class ExtractionDtos {

    public record ExtractionTriggerRequest(
            @Valid List<@Positive(message = "论文 id 必须是正整数") Long> paperIds) {
    }

    /** 触发结果：202 立即返回，queuedCount 是进入异步队列的篇数 */
    public record ExtractionTriggerResponse(String message, int queuedCount, List<Long> paperIds) {
    }

    /** 单篇抽取状态（前端 2 秒轮询用） */
    public record ExtractionStatusDto(
            Long paperId,
            String paperTitle,
            String status,
            long extractedEntityCount,
            long extractedRelationshipCount) {
    }

    /** 台账实体行（明细面板展示） */
    public record ExtractionEntityDto(
            Long id,
            String name,
            String type,
            String resolvedEntityType,
            Long resolvedEntityId) {
    }

    /** 关系行：端点回填成名称便于直接阅读 */
    public record ExtractionRelationshipDto(
            Long id,
            String sourceName,
            String targetName,
            String type,
            String evidence,
            Double confidence) {
    }

    /** 单篇抽取明细 */
    public record ExtractionResultDto(
            Long paperId,
            String paperTitle,
            String status,
            List<ExtractionEntityDto> entities,
            List<ExtractionRelationshipDto> relationships) {
    }
}
