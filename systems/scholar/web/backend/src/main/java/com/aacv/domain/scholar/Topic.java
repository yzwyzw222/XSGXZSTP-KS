package com.aacv.domain.scholar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 研究主题表：论文所属主题/领域。
 */
@Entity
@Table(name = "topic", indexes = {
        @Index(name = "idx_topic_normalized_name", columnList = "normalized_name")
})
public class Topic extends ScholarEntity {

    @Id
    @Column(name = "topic_id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}