package com.xsyu.academicgraph.domain.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 发表渠道仓储 */
public interface VenueRepository extends JpaRepository<Venue, Long> {

    List<Venue> findByIdIn(Collection<Long> ids);

    Optional<Venue> findFirstByDisplayNameIgnoreCase(String displayName);

    /** 渠道列表：按名称模糊搜索 + 分页 */
    Page<Venue> findByDisplayNameContainingIgnoreCase(String keyword, Pageable pageable);
}
