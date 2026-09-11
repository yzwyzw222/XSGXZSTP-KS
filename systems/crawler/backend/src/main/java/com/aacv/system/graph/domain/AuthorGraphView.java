package com.aacv.system.graph.domain;

import java.util.List;

/** 按成果分页，图中的共同作者只对应当前页作品。 */
public record AuthorGraphView(GraphView graph, int page, int size, long totalWorks) {

    public enum WorkCategory {
        PAPER(List.of("article", "review", "preprint", "proceedings-article")),
        PATENT(List.of("patent")),
        MASTER_THESIS(List.of("master-thesis")),
        DOCTORAL_THESIS(List.of("doctoral-thesis"));

        private final List<String> achievementTypes;

        WorkCategory(List<String> achievementTypes) { this.achievementTypes = achievementTypes; }

        public List<String> achievementTypes() { return achievementTypes; }
    }
}
