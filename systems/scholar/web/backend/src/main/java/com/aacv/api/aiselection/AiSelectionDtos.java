package com.aacv.api.aiselection;

/**
 * AI 选文分析接口 DTO 集合。
 */
public final class AiSelectionDtos {

    private AiSelectionDtos() {
    }

    /** 数据来源条目：来源库名称（paper_type）+ 论文数。 */
    public record SourceItem(String name, long paperCount) {
    }
}
