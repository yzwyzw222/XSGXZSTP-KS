package com.xsyu.academicgraph.domain.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 论文引用关系（对应 MySQL 表 paper_reference）。
 * citing = 施引论文，cited = 被引论文；被引文献不在库内时只记录 DOI 线索（external_cited_doi）。
 * 注意：cited_paper_id 参与外键 ON DELETE SET NULL，所以 MySQL 不允许再对它加 CHECK，
 *       "cited_paper_id 与 external_cited_doi 至少一个非空"的校验在应用层（PaperService）完成。
 */
@Entity
@Table(name = "paper_reference")
@Getter
@Setter
public class PaperReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "citing_paper_id", nullable = false)
    private Long citingPaperId;

    @Column(name = "cited_paper_id")
    private Long citedPaperId;

    @Column(name = "external_cited_doi", length = 255)
    private String externalCitedDoi;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
