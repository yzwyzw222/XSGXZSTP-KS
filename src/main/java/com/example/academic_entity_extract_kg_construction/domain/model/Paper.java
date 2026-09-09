package com.example.academic_entity_extract_kg_construction.domain.model;

import com.example.academic_entity_extract_kg_construction.domain.model.enums.ExtractionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "papers")
@Getter
@Setter
public class Paper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "semantic_scholar_id", unique = true, length = 64)
    private String semanticScholarId;

    @Column(nullable = false, length = 1024)
    private String title;

    @Column(name = "abstract_text", columnDefinition = "TEXT")
    private String abstractText;

    private Integer year;

    @Column(length = 255)
    private String doi;

    @Column(name = "citation_count")
    private Integer citationCount = 0;

    @Column(name = "reference_count")
    private Integer referenceCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 32)
    private ExtractionStatus extractionStatus = ExtractionStatus.PENDING;

    @Column(length = 512)
    private String url;

    @ManyToMany
    @JoinTable(name = "paper_authors",
            joinColumns = @JoinColumn(name = "paper_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    private Set<Author> authors = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "paper_citations",
            joinColumns = @JoinColumn(name = "citing_paper_id"),
            inverseJoinColumns = @JoinColumn(name = "cited_paper_id"))
    private Set<Paper> references = new HashSet<>();

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
