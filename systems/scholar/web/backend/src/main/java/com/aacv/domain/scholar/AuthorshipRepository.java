package com.aacv.domain.scholar;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuthorshipRepository extends JpaRepository<Authorship, String> {
    List<Authorship> findByPaperIdIn(List<String> paperIds);
    List<Authorship> findByPaperId(String paperId);
    List<Authorship> findByAuthorId(String authorId);
    Page<Authorship> findByAuthorId(String authorId, Pageable pageable);

    // 统计：按作者统计论文数（Top N）
    @Query("select a.authorId, count(a) as cnt from Authorship a group by a.authorId order by count(a) desc")
    List<Object[]> countByAuthor();

    // 统计：按机构统计署名次数（Top N）
    @Query("select a.institutionId, count(a) as cnt from Authorship a where a.institutionId is not null group by a.institutionId order by count(a) desc")
    List<Object[]> countByInstitution();
}