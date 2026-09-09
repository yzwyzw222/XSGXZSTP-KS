package com.xsyu.academicgraph.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 角色字典（对应 MySQL 表 sys_role）。
 * 种子数据在库中已固定：1=ADMIN（管理员）、2=ANALYST（普通用户），
 * 因此这里使用固定主键（@Id 而非自增），与初始化数据一一对应。
 */
@Entity
@Table(name = "sys_role")
@Getter
@Setter
public class SysRole {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_ANALYST = "ANALYST";

    public static final long ID_ADMIN = 1L;
    public static final long ID_ANALYST = 2L;

    @Id
    private Long id;

    @Column(name = "role_code", nullable = false, unique = true, length = 32)
    private String roleCode;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
