package com.example.academic_entity_extract_kg_construction.domain.repository;

import com.example.academic_entity_extract_kg_construction.domain.model.Paper;
import com.example.academic_entity_extract_kg_construction.domain.model.enums.ExtractionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaperRepository extends JpaRepository<Paper, Long> {

    Optional<Paper> findBySemanticScholarId(String semanticScholarId);

    @Query("SELECT p FROM Paper p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Paper> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p FROM Paper p WHERE p.year = :year")
    Page<Paper> findByYear(@Param("year") Integer year, Pageable pageable);

    @Query("SELECT p FROM Paper p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) AND p.year = :year")
    Page<Paper> searchByKeywordAndYear(@Param("keyword") String keyword, @Param("year") Integer year, Pageable pageable);

    List<Paper> findByExtractionStatus(ExtractionStatus status);

    long countByExtractionStatus(ExtractionStatus status);
}
