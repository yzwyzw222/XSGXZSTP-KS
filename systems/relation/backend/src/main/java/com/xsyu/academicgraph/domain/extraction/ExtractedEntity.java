package com.xsyu.academicgraph.domain.extraction;

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

import java.time.LocalDateTime;
import java.util.Set;

/**
 * LLM 抽取实体台账（对应 MySQL 表 extracted_entities）——六类实体全记账，
 * 是 entity_relationship 关系表的外键端点，也是「抽出了什么」的审计凭据。
 * resolved_entity_type / resolved_entity_id 记录该实体归并进了哪张业务表
 * （AUTHOR/INSTITUTION/KEYWORD/RESEARCH_ENTITY），未解析成功时为空。
 */
@Entity
@Table(name = "extracted_entities")
@Getter
@Setter
public class ExtractedEntity {

    public static final String TYPE_PERSON = "PERSON";
    public static final String TYPE_ORGANIZATION = "ORGANIZATION";
    public static final String TYPE_TOPIC = "TOPIC";
    public static final String TYPE_METHOD = "METHOD";
    public static final String TYPE_DATASET = "DATASET";
    public static final String TYPE_TOOL = "TOOL";

    /** 合法类型白名单：与数据库 ck_extracted_entities_type 检查约束保持一致 */
    public static final Set<String> VALID_TYPES = Set.of(
            TYPE_PERSON, TYPE_ORGANIZATION, TYPE_TOPIC, TYPE_METHOD, TYPE_DATASET, TYPE_TOOL);

    /** resolved_entity_type 取值 */
    public static final String RESOLVED_AUTHOR = "AUTHOR";
    public static final String RESOLVED_INSTITUTION = "INSTITUTION";
    public static final String RESOLVED_KEYWORD = "KEYWORD";
    public static final String RESOLVED_RESEARCH_ENTITY = "RESEARCH_ENTITY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "entity_name", nullable = false, length = 512)
    private String entityName;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    /** LLM 附带属性（JSON 原文，MySQL JSON 列） */
    @Column(columnDefinition = "JSON")
    private String properties;

    @Column(name = "resolved_entity_type", length = 32)
    private String resolvedEntityType;

    @Column(name = "resolved_entity_id")
    private Long resolvedEntityId;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
