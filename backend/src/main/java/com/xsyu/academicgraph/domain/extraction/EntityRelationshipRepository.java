package com.xsyu.academicgraph.domain.extraction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 抽取实体关系仓储：重跑抽取时按论文清空重建 */
public interface EntityRelationshipRepository extends JpaRepository<EntityRelationship, Long> {

    List<EntityRelationship> findByPaperIdOrderByIdAsc(Long paperId);

    void deleteByPaperId(Long paperId);

    long countByPaperId(Long paperId);

    /** 图谱查询：按论文批量取关系行 */
    List<EntityRelationship> findByPaperIdIn(List<Long> paperIds);
}
