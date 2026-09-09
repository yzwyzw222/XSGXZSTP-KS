package com.xsyu.academicgraph.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** 角色仓储：角色是固定字典，只做查询不做增删改 */
public interface RoleRepository extends JpaRepository<SysRole, Long> {

    List<SysRole> findByRoleCodeIn(Collection<String> roleCodes);
}
