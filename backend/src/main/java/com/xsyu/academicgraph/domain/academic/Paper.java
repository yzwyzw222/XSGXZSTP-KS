package com.xsyu.academicgraph.domain.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 论文/专利等学术成果（对应 MySQL 表 paper），平台的权威核心数据。
 * publication_year 是数据库生成列（YEAR(publication_date)），只读，不参与插入/更新。
 * 外键 venue_id / created_by 在 JPA 中直接映射为普通列，完整性由数据库外键约束兜底。
 */
@Entity
@Table(name = "paper")
@Getter
@Setter
public class Paper {

    public static final String TYPE_JOURNAL = "JOURNAL_ARTICLE";
    public static final String TYPE_CONFERENCE = "CONFERENCE_PAPER";
    public static final String TYPE_PATENT = "PATENT";
    public static final String TYPE_OTHER = "OTHER";

    /** LLM 实体抽取状态取值（ExtractionService 维护，与数据库 CHECK 约束一致） */
    public static final String EXTRACTION_PENDING = "PENDING";
    public static final String EXTRACTION_IN_PROGRESS = "IN_PROGRESS";
    public static final String EXTRACTION_COMPLETED = "COMPLETED";
    public static final String EXTRACTION_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(unique = true, length = 255)
    private String doi;

    @Column(name = "paper_type", nullable = false, length = 32)
    private String paperType = TYPE_JOURNAL;

    @Column(length = 16)
    private String language;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    /** 生成列：MySQL 由 publication_date 自动算出，Hibernate 只能读不能写 */
    @Column(name = "publication_year", insertable = false, updatable = false)
    private Short publicationYear;

    @Column(name = "abstract_text", columnDefinition = "MEDIUMTEXT")
    private String abstractText;

    @Column(name = "citation_count", nullable = false)
    private Integer citationCount = 0;

    @Column(name = "venue_id")
    private Long venueId;

    // ------------------------------------------------------------------
    // 以下 5 个字段来自知网（CNKI）导出，原有论文表没有对应列，为保存真实数据而扩展。
    // 卷/期/页码用字符串存：知网导出的期号可能含字母（如 "Z3"）、页码是区间（如 "20-22"），
    // 中图分类号一篇文章可能多个（分号分隔），都不是纯数字。
    // ------------------------------------------------------------------

    /** 卷（CNKI 导入） */
    @Column(length = 32)
    private String volume;

    /** 期（CNKI 导入，可能含字母，如 "Z3"） */
    @Column(length = 32)
    private String period;

    /** 页码（CNKI 导入，区间形式，如 "20-22"） */
    @Column(name = "page_count", length = 32)
    private String pageCount;

    /** 中图分类号（CNKI 导入，多分类用分号分隔） */
    @Column(name = "clc_number", length = 64)
    private String clcNumber;

    /** 知网原文链接（CNKI 导入） */
    @Column(length = 500)
    private String url;

    /** LLM 实体抽取状态：PENDING/IN_PROGRESS/COMPLETED/FAILED（抽取服务维护） */
    @Column(name = "extraction_status", nullable = false, length = 32)
    private String extractionStatus = EXTRACTION_PENDING;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
