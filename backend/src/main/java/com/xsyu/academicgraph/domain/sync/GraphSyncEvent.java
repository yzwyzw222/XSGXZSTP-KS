package com.xsyu.academicgraph.domain.sync;

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
 * 图同步 Outbox 事件（对应 MySQL 表 graph_sync_event）。
 * 事务性 Outbox 模式：业务数据变更时，在同一数据库事务里插入一条待同步事件；
 * 后台调度器（GraphSyncService）轮询 PENDING 事件投影到 Neo4j。
 * 好处：MySQL 事务成功才产生事件，图投影可重试、可重建，不会出现"库里有、图里没有"的半成功状态。
 */
@Entity
@Table(name = "graph_sync_event")
@Getter
@Setter
public class GraphSyncEvent {

    public static final String ENTITY_PAPER = "PAPER";
    public static final String ENTITY_AUTHOR = "AUTHOR";
    public static final String ENTITY_INSTITUTION = "INSTITUTION";
    public static final String ENTITY_VENUE = "VENUE";
    public static final String ENTITY_KEYWORD = "KEYWORD";
    public static final String ENTITY_RESEARCH_ENTITY = "RESEARCH_ENTITY";

    public static final String EVENT_UPSERT = "UPSERT";
    public static final String EVENT_DELETE = "DELETE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType = EVENT_UPSERT;

    @Column(nullable = false, length = 16)
    private String status = STATUS_PENDING;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
