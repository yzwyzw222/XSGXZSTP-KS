package com.xsyu.academicgraph.domain.academic;

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
 * 论文-作者署名（对应 MySQL 表 paper_author）。
 * 复合主键 (paper_id, author_id)；author_position 记录作者位次（1 为第一作者），
 * institution_id 是"作者属于某机构"的证据，也是图谱 AFFILIATED_WITH 关系的来源。
 */
@Entity
@Table(name = "paper_author")
@IdClass(PaperAuthor.PaperAuthorId.class)
@Getter
@Setter
public class PaperAuthor {

    @Id
    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Id
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_position", nullable = false)
    private Integer authorPosition;

    @Column(name = "institution_id")
    private Long institutionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Getter
    @Setter
    public static class PaperAuthorId implements Serializable {
        private Long paperId;
        private Long authorId;

        public PaperAuthorId() {
        }

        public PaperAuthorId(Long paperId, Long authorId) {
            this.paperId = paperId;
            this.authorId = authorId;
        }
    }
}
