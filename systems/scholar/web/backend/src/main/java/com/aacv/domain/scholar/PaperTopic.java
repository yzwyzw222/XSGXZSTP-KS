package com.aacv.domain.scholar;

import com.aacv.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 论文-主题关系表。
 */
@Entity
@Table(name = "paper_topic",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uk_paper_topic", columnNames = {"paper_id", "topic_id"}),
        indexes = {
                @Index(name = "idx_paper_topic_paper", columnList = "paper_id"),
                @Index(name = "idx_paper_topic_topic", columnList = "topic_id")
        })
public class PaperTopic extends BaseEntity {

    @Column(name = "paper_id", nullable = false, length = 64)
    private String paperId;

    @Column(name = "topic_id", nullable = false, length = 64)
    private String topicId;

    public String getPaperId() {
        return paperId;
    }

    public void setPaperId(String paperId) {
        this.paperId = paperId;
    }

    public String getTopicId() {
        return topicId;
    }

    public void setTopicId(String topicId) {
        this.topicId = topicId;
    }
}