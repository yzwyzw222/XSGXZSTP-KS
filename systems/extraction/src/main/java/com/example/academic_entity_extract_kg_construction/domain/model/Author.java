package com.example.academic_entity_extract_kg_construction.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "authors")
@Getter
@Setter
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "semantic_scholar_id", unique = true, length = 64)
    private String semanticScholarId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 512)
    private String affiliation;

    @Column(name = "h_index")
    private Integer hIndex = 0;

    @Column(name = "paper_count")
    private Integer paperCount = 0;

    @Column(name = "citation_count")
    private Integer citationCount = 0;

    @Column(length = 512)
    private String url;

    @Version
    private Integer version;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
