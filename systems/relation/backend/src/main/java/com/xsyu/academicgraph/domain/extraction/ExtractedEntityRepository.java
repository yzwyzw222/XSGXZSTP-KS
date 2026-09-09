package com.xsyu.academicgraph.domain.extraction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 抽取实体台账仓储：重跑抽取时按论文清空重建 */
public interface ExtractedEntityRepository extends JpaRepository<ExtractedEntity, Long> {

    List<ExtractedEntity> findByPaperIdOrderByIdAsc(Long paperId);

    void deleteByPaperId(Long paperId);

    List<ExtractedEntity> findAllByIdIn(List<Long> ids);

    long countByPaperId(Long paperId);

    /** 图谱查询：只取已归并到业务表的实体（未 resolve 的端点画不出边） */
    List<ExtractedEntity> findByResolvedEntityTypeNotNull();

    List<ExtractedEntity> findByPaperIdInAndResolvedEntityTypeNotNull(List<Long> paperIds);
}
