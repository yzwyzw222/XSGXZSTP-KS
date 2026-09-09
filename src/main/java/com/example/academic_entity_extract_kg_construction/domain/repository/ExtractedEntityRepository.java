package com.example.academic_entity_extract_kg_construction.domain.repository;

import com.example.academic_entity_extract_kg_construction.domain.model.ExtractedEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtractedEntityRepository extends JpaRepository<ExtractedEntity, Long> {

    List<ExtractedEntity> findByPaperId(Long paperId);
}
