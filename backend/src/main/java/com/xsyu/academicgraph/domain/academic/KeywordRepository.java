package com.xsyu.academicgraph.domain.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 关键词仓储 */
public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    Optional<Keyword> findByName(String name);

    boolean existsByName(String name);

    Page<Keyword> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    List<Keyword> findByIdIn(Collection<Long> ids);
}
