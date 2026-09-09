package com.xsyu.academicgraph.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户仓储：继承 JpaRepository 后自动获得 save/findById/delete 等能力，
 * 复杂查询用"方法名约定"（findByXxxContaining 自动生成 LIKE 查询）声明即可。
 */
public interface UserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 后台用户管理列表：按登录名模糊搜索 + 分页 */
    Page<SysUser> findByUsernameContainingIgnoreCase(String keyword, Pageable pageable);
}
