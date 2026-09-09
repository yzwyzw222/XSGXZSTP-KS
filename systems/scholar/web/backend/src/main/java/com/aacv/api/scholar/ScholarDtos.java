package com.aacv.api.scholar;

import java.util.List;

/**
 * 学者画像 DTO 集合。
 */
public final class ScholarDtos {

    private ScholarDtos() {
    }

    /** 学者搜索结果项。 */
    public record ScholarSummary(String id, String name, long paperCount, String institution) {
    }

    /** 论文简要信息。 */
    public record PaperBrief(String title, Integer year, String venue, String paperType,
                             boolean firstAuthor, boolean corresponding) {
    }

    /** 学者完整画像。 */
    public record ScholarProfile(
            String id,
            String name,
            long totalPapers,
            long firstAuthorPapers,
            long correspondingPapers,
            Integer firstYear,
            Integer lastYear,
            List<YearCount> trend,
            List<NameCount> topics,
            List<NameCount> venues,
            List<NameCount> coauthors,
            List<NameCount> institutions,
            List<PaperBrief> papers
    ) {
    }

    /** 年份-数量。 */
    public record YearCount(Integer year, long count) {
    }

    /** 名称-数量。 */
    public record NameCount(String name, long count) {
    }
}
