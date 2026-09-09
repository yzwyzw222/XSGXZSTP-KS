package com.xsyu.academicgraph.domain.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 论文-关键词关联仓储 */
public interface PaperKeywordRepository extends JpaRepository<PaperKeyword, PaperKeyword.PaperKeywordId> {

    List<PaperKeyword> findByPaperIdOrderByKeywordPositionAsc(Long paperId);

    List<PaperKeyword> findByPaperIdIn(Collection<Long> paperIds);

    void deleteByPaperId(Long paperId);

    long countByKeywordId(Long keywordId);

    /** 该论文当前最大关键词位次（LLM 抽取追加主题时从 max+1 继续编号） */
    Optional<PaperKeyword> findTop1ByPaperIdOrderByKeywordPositionDesc(Long paperId);
}
