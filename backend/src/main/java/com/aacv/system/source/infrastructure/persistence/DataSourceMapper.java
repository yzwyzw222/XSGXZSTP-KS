package com.aacv.system.source.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DataSourceMapper {

    long countAll();

    int countByCode(@Param("sourceCode") String sourceCode);

    List<DataSourceRow> findPage(@Param("offset") long offset, @Param("size") int size);

    DataSourceRow findById(@Param("id") long id);

    DataSourceRow lockById(@Param("id") long id);

    int insert(DataSourceRow row);

    int update(@Param("row") DataSourceRow row, @Param("expectedVersion") long expectedVersion);

    int updateEnabled(
            @Param("id") long id,
            @Param("enabled") boolean enabled,
            @Param("expectedVersion") long expectedVersion);

    int updateProbeResult(
            @Param("id") long id,
            @Param("reachable") boolean reachable,
            @Param("checkedAt") Instant checkedAt);
}
