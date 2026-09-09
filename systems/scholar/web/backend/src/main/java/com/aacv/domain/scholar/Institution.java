package com.aacv.domain.scholar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 机构表：作者所属机构信息。
 */
@Entity
@Table(name = "institution", indexes = {
        @Index(name = "idx_institution_normalized_name", columnList = "normalized_name")
})
public class Institution extends ScholarEntity {

    @Id
    @Column(name = "institution_id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 512)
    private String normalizedName;

    @Column(name = "country", length = 128)
    private String country;

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

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getHomepage() {
        return homepage;
    }

    public void setHomepage(String homepage) {
        this.homepage = homepage;
    }
}