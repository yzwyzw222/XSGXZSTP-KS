package com.xsyu.academicgraph.domain.extraction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 论文-研究实体关联仓储：重跑抽取时按论文清空重建 */
public interface PaperResearchEntityRepository
        extends JpaRepository<PaperResearchEntity, PaperResearchEntity.PaperResearchEntityId> {

    List<PaperResearchEntity> findByPaperId(Long paperId);

    void deleteByPaperId(Long paperId);

    boolean existsByPaperIdAndResearchEntityId(Long paperId, Long researchEntityId);
}
