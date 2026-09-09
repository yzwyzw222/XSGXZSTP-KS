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
 * 论文-关键词关联（对应 MySQL 表 paper_keyword）。
 * keyword_position 记录关键词位次（数据库唯一约束保证同篇论文位次不重复）。
 */
@Entity
@Table(name = "paper_keyword")
@IdClass(PaperKeyword.PaperKeywordId.class)
@Getter
@Setter
public class PaperKeyword {

    @Id
    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Id
    @Column(name = "keyword_id", nullable = false)
    private Long keywordId;

    @Column(name = "keyword_position", nullable = false)
    private Integer keywordPosition = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Getter
    @Setter
    public static class PaperKeywordId implements Serializable {
        private Long paperId;
        private Long keywordId;

        public PaperKeywordId() {
        }

        public PaperKeywordId(Long paperId, Long keywordId) {
            this.paperId = paperId;
            this.keywordId = keywordId;
        }
    }
}
