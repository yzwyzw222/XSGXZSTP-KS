package com.xsyu.academicgraph.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-角色关联（对应 MySQL 表 sys_user_role，多对多中间表）。
 * 复合主键 (user_id, role_id) 通过 @IdClass 映射，保证同一用户同一角色只授权一次。
 * 用户被删除时该行级联删除（数据库外键 ON DELETE CASCADE），JPA 侧只负责读写。
 */
@Entity
@Table(name = "sys_user_role")
@IdClass(SysUserRole.UserRoleId.class)
@Getter
@Setter
public class SysUserRole {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    /** 复合主键类：字段名与实体中的 @Id 字段一一对应 */
    @Getter
    @Setter
    public static class UserRoleId implements Serializable {
        private Long userId;
        private Long roleId;

        public UserRoleId() {
        }

        public UserRoleId(Long userId, Long roleId) {
            this.userId = userId;
            this.roleId = roleId;
        }
    }
}
