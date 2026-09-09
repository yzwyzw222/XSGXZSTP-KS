package com.aacv.domain.scholar;

import com.aacv.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 论文引用关系表：citing_paper_id 引用 cited_paper_id。
 */
@Entity
@Table(name = "paper_reference",
        uniqueConstraints = @jakarta.persistence.UniqueConstraint(
                name = "uk_paper_reference", columnNames = {"citing_paper_id", "cited_paper_id"}),
        indexes = {
                @Index(name = "idx_paper_ref_citing", columnList = "citing_paper_id"),
                @Index(name = "idx_paper_ref_cited", columnList = "cited_paper_id")
        })
public class PaperReference extends BaseEntity {

    @Column(name = "citing_paper_id", nullable = false, length = 64)
    private String citingPaperId;

    @Column(name = "cited_paper_id", nullable = false, length = 64)
    private String citedPaperId;

    public String getCitingPaperId() {
        return citingPaperId;
    }

    public void setCitingPaperId(String citingPaperId) {
        this.citingPaperId = citingPaperId;
    }

    public String getCitedPaperId() {
        return citedPaperId;
    }

    public void setCitedPaperId(String citedPaperId) {
        this.citedPaperId = citedPaperId;
    }
}