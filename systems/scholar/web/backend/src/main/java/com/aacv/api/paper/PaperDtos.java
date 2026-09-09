package com.aacv.api.paper;

/**
 * 论文检索接口 DTO 集合。
 */
public final class PaperDtos {

    private PaperDtos() {
    }

    /** 论文条目：id + 标题 + 年份 + 主题数。 */
    public record PaperItem(String id, String title, Integer year, long topicCount) {
    }

    /** 论文下的主题条目。 */
    public record TopicItem(String id, String name) {
    }
}
