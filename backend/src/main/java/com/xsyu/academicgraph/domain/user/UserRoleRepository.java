package com.xsyu.academicgraph.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** 用户-角色关联仓储：按用户查角色、批量重置用户角色时使用 */
public interface UserRoleRepository extends JpaRepository<SysUserRole, SysUserRole.UserRoleId> {

    List<SysUserRole> findByUserId(Long userId);

    List<SysUserRole> findByUserIdIn(Collection<Long> userIds);

    void deleteByUserId(Long userId);
}
