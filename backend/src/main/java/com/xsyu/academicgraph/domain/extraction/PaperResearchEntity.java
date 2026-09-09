package com.xsyu.academicgraph.domain.extraction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 论文-研究实体关联（对应 MySQL 表 paper_research_entity）。
 * 复合主键 (paper_id, research_entity_id)；重跑抽取时按 paper_id 整表覆盖。
 */
@Entity
@Table(name = "paper_research_entity")
@IdClass(PaperResearchEntity.PaperResearchEntityId.class)
@Getter
@Setter
public class PaperResearchEntity {

    @Id
    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Id
    @Column(name = "research_entity_id", nullable = false)
    private Long researchEntityId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Getter
    @Setter
    public static class PaperResearchEntityId implements Serializable {
        private Long paperId;
        private Long researchEntityId;

        public PaperResearchEntityId() {
        }

        public PaperResearchEntityId(Long paperId, Long researchEntityId) {
            this.paperId = paperId;
            this.researchEntityId = researchEntityId;
        }
    }
}
