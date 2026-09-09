package com.aacv.domain.scholar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaperRepository extends JpaRepository<Paper, String> {
    // 图谱初始子图：取最新 N 篇论文
    List<Paper> findTop10ByOrderByPublicationDateDesc();

    // 统计：按期刊统计论文数（Top N）
    @Query("select p.venueId, count(p) as cnt from Paper p where p.venueId is not null group by p.venueId order by count(p) desc")
    List<Object[]> countByVenue();

    // 统计：按年份统计论文数（发表趋势）
    @Query("select p.year, count(p) as cnt from Paper p where p.year is not null group by p.year order by p.year")
    List<Object[]> countByYear();

    // 统计：按论文类型统计
    @Query("select p.paperType, count(p) as cnt from Paper p where p.paperType is not null group by p.paperType order by count(p) desc")
    List<Object[]> countByType();
}