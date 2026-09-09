package com.aacv.system.operations.infrastructure.persistence;

import com.aacv.system.operations.domain.AuditQuery;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {

    int insert(AuditLogRow row);

    long countAll(@Param("query") AuditQuery query);

    List<AuditLogRow> findPage(@Param("offset") long offset, @Param("size") int size,
            @Param("query") AuditQuery query);
}
