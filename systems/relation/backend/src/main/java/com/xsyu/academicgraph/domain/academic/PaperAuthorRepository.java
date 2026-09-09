package com.xsyu.academicgraph.domain.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 论文-作者署名仓储 */
public interface PaperAuthorRepository extends JpaRepository<PaperAuthor, PaperAuthor.PaperAuthorId> {

    List<PaperAuthor> findByPaperIdOrderByAuthorPositionAsc(Long paperId);

    List<PaperAuthor> findByPaperIdIn(Collection<Long> paperIds);

    void deleteByPaperId(Long paperId);

    long countByAuthorId(Long authorId);

    List<PaperAuthor> findByAuthorId(Long authorId);

    /** 该论文当前最大署名位次（LLM 抽取追加作者时从 max+1 继续编号） */
    Optional<PaperAuthor> findTop1ByPaperIdOrderByAuthorPositionDesc(Long paperId);
}
