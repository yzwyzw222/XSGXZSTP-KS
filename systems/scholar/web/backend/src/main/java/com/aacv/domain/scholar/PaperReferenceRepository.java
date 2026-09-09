package com.aacv.domain.scholar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaperReferenceRepository extends JpaRepository<PaperReference, Long> {
    List<PaperReference> findByCitingPaperIdIn(List<String> paperIds);
    List<PaperReference> findByCitingPaperId(String paperId);

    // 统计：按被引论文统计被引次数（Top N）
    @Query("select r.citedPaperId, count(r) as cnt from PaperReference r group by r.citedPaperId order by count(r) desc")
    List<Object[]> countByCited();
}