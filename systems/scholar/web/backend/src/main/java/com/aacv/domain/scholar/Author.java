package com.aacv.domain.scholar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 作者表：保存作者基本信息。
 */
@Entity
@Table(name = "author", indexes = {
        @Index(name = "idx_author_normalized_name", columnList = "normalized_name")
})
public class Author extends ScholarEntity {

    @Id
    @Column(name = "author_id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 512)
    private String normalizedName;

    @Column(name = "orcid", length = 32, unique = true)
    private String orcid;

    @Column(name = "homepage", length = 1024)
    private String homepage;

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

    public String getOrcid() {
        return orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }

    public String getHomepage() {
        return homepage;
    }

    public void setHomepage(String homepage) {
        this.homepage = homepage;
    }
}