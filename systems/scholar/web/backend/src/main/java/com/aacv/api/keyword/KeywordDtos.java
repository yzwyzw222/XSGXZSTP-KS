package com.aacv.api.keyword;

import java.util.List;

/**
 * 关键词检索接口 DTO 集合。
 */
public final class KeywordDtos {

    private KeywordDtos() {
    }

    /** 关键词条目：id + 名称 + 关联论文数。 */
    public record KeywordItem(String id, String name, long paperCount) {
    }

    /** 关键词下的论文行。 */
    public record PaperRow(String id, String title, String venue, Integer year,
                           String paperType, List<String> authors) {
    }
}
