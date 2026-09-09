package com.example.academic_entity_extract_kg_construction.domain.repository;

import com.example.academic_entity_extract_kg_construction.domain.model.ResearchTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResearchTopicRepository extends JpaRepository<ResearchTopic, Long> {

    Optional<ResearchTopic> findByName(String name);
}
