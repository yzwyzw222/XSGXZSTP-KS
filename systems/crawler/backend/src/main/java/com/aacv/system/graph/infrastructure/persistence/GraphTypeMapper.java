package com.aacv.system.graph.infrastructure.persistence;

import com.aacv.system.graph.domain.GraphTypeDefinition;
import com.aacv.system.graph.domain.GraphTypeDefinition.Kind;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GraphTypeMapper {
    List<GraphTypeDefinition> findAll();
    GraphTypeDefinition find(@Param("kind") Kind kind, @Param("code") String code);
    int update(GraphTypeDefinition definition);
}
