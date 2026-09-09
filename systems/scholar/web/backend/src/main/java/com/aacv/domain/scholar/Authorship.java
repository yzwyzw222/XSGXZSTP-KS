package com.aacv.domain.scholar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 作者-论文关系表：记录作者在论文中的署名顺序、通讯标记与机构信息。
 */
@Entity
@Table(name = "authorship", indexes = {
        @Index(name = "idx_authorship_author", columnList = "author_id"),
        @Index(name = "idx_authorship_paper", columnList = "paper_id"),
        @Index(name = "idx_authorship_institution", columnList = "institution_id")
})
public class Authorship extends ScholarEntity {

    @Id
    @Column(name = "authorship_id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "author_id", nullable = false, length = 64)
    private String authorId;

    @Column(name = "paper_id", nullable = false, length = 64)
    private String paperId;

    @Column(name = "author_order")
    private Integer authorOrder;

    @Column(name = "is_corresponding", nullable = false)
    private boolean corresponding = false;

    @Column(name = "institution_id", length = 64)
    private String institutionId;

    @Column(name = "raw_affiliation", columnDefinition = "TEXT")
    private String rawAffiliation;

    @Column(name = "source", length = 64)
    private String source;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getPaperId() {
        return paperId;
    }

    public void setPaperId(String paperId) {
        this.paperId = paperId;
    }

    public Integer getAuthorOrder() {
        return authorOrder;
    }

    public void setAuthorOrder(Integer authorOrder) {
        this.authorOrder = authorOrder;
    }

    public boolean isCorresponding() {
        return corresponding;
    }

    public void setCorresponding(boolean corresponding) {
        this.corresponding = corresponding;
    }

    public String getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(String institutionId) {
        this.institutionId = institutionId;
    }

    public String getRawAffiliation() {
        return rawAffiliation;
    }

    public void setRawAffiliation(String rawAffiliation) {
        this.rawAffiliation = rawAffiliation;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}