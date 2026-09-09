package com.xsyu.academicgraph.domain.user;

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

/**
 * 用户账号（对应 MySQL 表 sys_user）。
 * 密码只存 BCrypt 哈希（password_hash 列），任何情况下都不存明文。
 * version 字段配合 @Version 实现乐观锁：并发修改时后提交的一方收到 409 冲突。
 */
@Entity
@Table(name = "sys_user")
@Getter
@Setter
public class SysUser {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 自增主键，由数据库生成
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", length = 64)
    private String displayName;

    @Column(nullable = false, length = 32)
    private String status = STATUS_ACTIVE;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp // 插入时由 Hibernate 填充当前时间
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp // 每次更新时自动刷新
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
