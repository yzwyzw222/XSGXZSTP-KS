package com.aacv.api.graph;

import java.util.List;

/**
 * 图谱统计数据响应：节点构成 + Top 作者/主题/期刊/机构。
 */
public class GraphStatsResponse {

    private long totalPapers;
    private long totalAuthors;
    private long totalInstitutions;
    private long totalVenues;
    private long totalTopics;

    private List<RankItem> topAuthors;
    private List<RankItem> topTopics;
    private List<RankItem> topVenues;
    private List<RankItem> topInstitutions;

    public GraphStatsResponse() {
    }

    public long getTotalPapers() {
        return totalPapers;
    }

    public void setTotalPapers(long totalPapers) {
        this.totalPapers = totalPapers;
    }

    public long getTotalAuthors() {
        return totalAuthors;
    }

    public void setTotalAuthors(long totalAuthors) {
        this.totalAuthors = totalAuthors;
    }

    public long getTotalInstitutions() {
        return totalInstitutions;
    }

    public void setTotalInstitutions(long totalInstitutions) {
        this.totalInstitutions = totalInstitutions;
    }

    public long getTotalVenues() {
        return totalVenues;
    }

    public void setTotalVenues(long totalVenues) {
        this.totalVenues = totalVenues;
    }

    public long getTotalTopics() {
        return totalTopics;
    }

    public void setTotalTopics(long totalTopics) {
        this.totalTopics = totalTopics;
    }

    public List<RankItem> getTopAuthors() {
        return topAuthors;
    }

    public void setTopAuthors(List<RankItem> topAuthors) {
        this.topAuthors = topAuthors;
    }

    public List<RankItem> getTopTopics() {
        return topTopics;
    }

    public void setTopTopics(List<RankItem> topTopics) {
        this.topTopics = topTopics;
    }

    public List<RankItem> getTopVenues() {
        return topVenues;
    }

    public void setTopVenues(List<RankItem> topVenues) {
        this.topVenues = topVenues;
    }

    public List<RankItem> getTopInstitutions() {
        return topInstitutions;
    }

    public void setTopInstitutions(List<RankItem> topInstitutions) {
        this.topInstitutions = topInstitutions;
    }

    /**
     * 排行项：name + count。
     */
    public static class RankItem {
        private final String name;
        private final long count;

        public RankItem(String name, long count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public long getCount() {
            return count;
        }
    }
}