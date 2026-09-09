package com.aacv.domain.scholar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * 论文表：保存论文基本信息。
 */
@Entity
@Table(name = "paper", indexes = {
        @Index(name = "idx_paper_year", columnList = "year"),
        @Index(name = "idx_paper_venue", columnList = "venue_id")
})
public class Paper extends ScholarEntity {

    @Id
    @Column(name = "paper_id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "`abstract`", columnDefinition = "TEXT")
    private String abstractText;

    @Column(name = "doi", length = 128, unique = true)
    private String doi;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "year")
    private Integer year;

    @Column(name = "paper_type", length = 64)
    private String paperType;

    @Column(name = "venue_id", length = 64)
    private String venueId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(LocalDate publicationDate) {
        this.publicationDate = publicationDate;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getPaperType() {
        return paperType;
    }

    public void setPaperType(String paperType) {
        this.paperType = paperType;
    }

    public String getVenueId() {
        return venueId;
    }

    public void setVenueId(String venueId) {
        this.venueId = venueId;
    }
}