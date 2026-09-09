package com.xsyu.academicgraph.domain.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 论文仓储。
 * findByFilters 用一条 JPQL 实现多条件可选过滤：
 * 参数为 null 时对应条件自动跳过（IS NULL OR ... 的写法让一条 SQL 覆盖所有组合），
 * 避免动态拼接 SQL 的繁琐与注入风险。
 */
public interface PaperRepository extends JpaRepository<Paper, Long> {

    Optional<Paper> findByDoi(String doi);

    /** 按标题精确查找：数据导入判重（跳过已存在的同题论文） */
    Optional<Paper> findFirstByTitleIgnoreCase(String title);

    @Query("""
            SELECT p FROM Paper p
            WHERE (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%'))
              AND (:paperType IS NULL OR p.paperType = :paperType)
              AND (:year IS NULL OR p.publicationYear = :year)
            """)
    Page<Paper> findByFilters(@Param("keyword") String keyword,
                              @Param("paperType") String paperType,
                              @Param("year") Short year,
                              Pageable pageable);

    /** 抽取待办队列：按状态取最早入库的前 10 篇（LLM 抽取批次入口） */
    List<Paper> findTop10ByExtractionStatusOrderByIdAsc(String status);

    long countByExtractionStatus(String status);
}
