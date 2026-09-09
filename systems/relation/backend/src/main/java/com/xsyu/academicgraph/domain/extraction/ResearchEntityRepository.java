package com.xsyu.academicgraph.domain.extraction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 研究实体仓储：按名称+类型精确查找供抽取去重复用（同名不同类视为不同实体） */
public interface ResearchEntityRepository extends JpaRepository<ResearchEntity, Long> {

    Optional<ResearchEntity> findFirstByNameAndEntityType(String name, String entityType);

    List<ResearchEntity> findByIdIn(List<Long> ids);
}
