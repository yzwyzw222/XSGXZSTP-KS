package com.xsyu.academicgraph.domain.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 机构仓储：按名称精确查找供数据导入去重复用 */
public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    Page<Institution> findByDisplayNameContainingIgnoreCase(String keyword, Pageable pageable);

    Optional<Institution> findFirstByDisplayNameIgnoreCase(String displayName);

    List<Institution> findByIdIn(List<Long> ids);
}
