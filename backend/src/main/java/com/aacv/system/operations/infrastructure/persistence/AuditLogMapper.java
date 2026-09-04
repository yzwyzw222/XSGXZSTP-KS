package com.aacv.system.operations.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {

    int insert(AuditLogRow row);

    long countAll();

    List<AuditLogRow> findPage(@Param("offset") long offset, @Param("size") int size);
}
