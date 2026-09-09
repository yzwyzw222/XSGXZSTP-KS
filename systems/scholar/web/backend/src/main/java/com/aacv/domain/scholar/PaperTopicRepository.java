package com.aacv.domain.scholar;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaperTopicRepository extends JpaRepository<PaperTopic, Long> {
    List<PaperTopic> findByPaperIdIn(List<String> paperIds);
    List<PaperTopic> findByPaperId(String paperId);
    List<PaperTopic> findByTopicId(String topicId);
    Page<PaperTopic> findByTopicId(String topicId, Pageable pageable);

    // 统计：按主题统计论文数（Top N）
    @Query("select pt.topicId, count(pt) as cnt from PaperTopic pt group by pt.topicId order by count(pt) desc")
    List<Object[]> countByTopic();
}