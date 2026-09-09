package com.xsyu.academicgraph.domain.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 作者仓储：列表支持按姓名模糊搜索；按名称精确查找供数据导入去重复用 */
public interface AuthorRepository extends JpaRepository<Author, Long> {

    Page<Author> findByDisplayNameContainingIgnoreCase(String keyword, Pageable pageable);

    Optional<Author> findFirstByDisplayNameIgnoreCase(String displayName);

    List<Author> findByIdIn(List<Long> ids);
}
