package com.aacv.system.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SessionMapper {

    int deleteByPrincipalName(@Param("principalName") String principalName);
}
