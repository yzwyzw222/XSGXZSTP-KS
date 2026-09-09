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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 研究实体（对应 MySQL 表 research_entity）——LLM 从论文标题+摘要抽出的
 * 方法（METHOD）/数据集（DATASET）/工具（TOOL）三类实体。
 * 与作者/机构/关键词不同，这三类在平台原有 schema 中没有对应表，
 * 因此独立建表承载；图投影为 (:ResearchEntity) 节点。
 */
@Entity
@Table(name = "research_entity")
@Getter
@Setter
public class ResearchEntity {

    public static final String TYPE_METHOD = "METHOD";
    public static final String TYPE_DATASET = "DATASET";
    public static final String TYPE_TOOL = "TOOL";

    /** 合法类型白名单：与数据库 ck_research_entity_type 检查约束保持一致 */
    public static final Set<String> VALID_TYPES = Set.of(TYPE_METHOD, TYPE_DATASET, TYPE_TOOL);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
