package com.xsyu.academicgraph.domain.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** 论文引用关系仓储 */
public interface PaperReferenceRepository extends JpaRepository<PaperReference, Long> {

    /** 某论文引用了哪些文献 */
    List<PaperReference> findByCitingPaperId(Long citingPaperId);

    /** 某论文被哪些库内论文引用（删除论文前检查用） */
    List<PaperReference> findByCitedPaperId(Long citedPaperId);

    List<PaperReference> findByCitingPaperIdIn(Collection<Long> citingPaperIds);

    void deleteByCitingPaperId(Long citingPaperId);
}
