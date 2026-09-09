package com.xsyu.academicgraph.domain.extraction;

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
import java.util.Set;

/**
 * LLM 抽取实体间关系（对应 MySQL 表 entity_relationship）。
 * 端点 source_entity_id / target_entity_id 指向 extracted_entities 台账行，
 * 归属一篇论文；同一论文内同一对端点同一关系类型只保留一条（唯一约束）。
 */
@Entity
@Table(name = "entity_relationship")
@Getter
@Setter
public class EntityRelationship {

    public static final String REL_USES = "USES";
    public static final String REL_EXTENDS = "EXTENDS";
    public static final String REL_EVALUATES_ON = "EVALUATES_ON";
    public static final String REL_APPLIED_TO = "APPLIED_TO";
    public static final String REL_PROPOSED_BY = "PROPOSED_BY";
    public static final String REL_COMPARED_WITH = "COMPARED_WITH";

    /** 合法关系类型白名单：与数据库 ck_entity_relationship_type 检查约束保持一致 */
    public static final Set<String> VALID_TYPES = Set.of(
            REL_USES, REL_EXTENDS, REL_EVALUATES_ON, REL_APPLIED_TO, REL_PROPOSED_BY, REL_COMPARED_WITH);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "source_entity_id", nullable = false)
    private Long sourceEntityId;

    @Column(name = "target_entity_id", nullable = false)
    private Long targetEntityId;

    @Column(name = "relationship_type", nullable = false, length = 32)
    private String relationshipType;

    /** LLM 给出的证据原文（可从原文核对） */
    @Column(name = "evidence_text", columnDefinition = "TEXT")
    private String evidenceText;

    /** 置信度：LLM 未给出时固定 1.0 */
    @Column(nullable = false)
    private Double confidence = 1.0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
