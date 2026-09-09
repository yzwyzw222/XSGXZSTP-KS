package com.aacv.system.infrastructure.database;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatabaseProbeMapper {

    Integer selectOne();
}
