package com.example.academic_entity_extract_kg_construction.domain.repository;

import com.example.academic_entity_extract_kg_construction.domain.model.EntityRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntityRelationshipRepository extends JpaRepository<EntityRelationship, Long> {

    List<EntityRelationship> findByPaperId(Long paperId);

    List<EntityRelationship> findBySourceEntityIdOrTargetEntityId(Long sourceId, Long targetId);
}
