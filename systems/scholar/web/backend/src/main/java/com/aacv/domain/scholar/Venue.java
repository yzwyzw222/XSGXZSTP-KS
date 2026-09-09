package com.aacv.domain.scholar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 期刊/会议表：论文发表载体信息。
 */
@Entity
@Table(name = "venue", indexes = {
        @Index(name = "idx_venue_normalized_name", columnList = "normalized_name")
})
public class Venue extends ScholarEntity {

    @Id
    @Column(name = "venue_id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 512)
    private String normalizedName;

    @Column(name = "type", length = 32)
    private String type;

    @Column(name = "publisher", length = 255)
    private String publisher;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }
}