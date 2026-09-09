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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 发表渠道：期刊/会议（对应 MySQL 表 venue）。
 * 论文通过 paper.venue_id 外键指向这里；渠道被删除时论文的 venue_id 置空（数据库 ON DELETE SET NULL）。
 */
@Entity
@Table(name = "venue")
@Getter
@Setter
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", length = 500)
    private String displayName;

    @Column(length = 16)
    private String issn;

    @Column(name = "venue_type", length = 64)
    private String venueType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
