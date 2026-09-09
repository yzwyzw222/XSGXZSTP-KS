package com.aacv.domain.scholar;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.io.Serializable;
import java.time.Instant;

/**
 * 学术图谱实体基类：审计时间戳。
 * 主键由各子类自行声明（外部来源的字符串编号，列名各不相同）。
 */
@MappedSuperclass
public abstract class ScholarEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}