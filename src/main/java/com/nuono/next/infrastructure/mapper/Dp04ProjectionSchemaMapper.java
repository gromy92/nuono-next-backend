package com.nuono.next.infrastructure.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Read-only information-schema probe for the exact DP-04 projection contract. */
public interface Dp04ProjectionSchemaMapper {

    @Select({
            "<script>",
            "SELECT CONCAT(LOWER(table_name), '.', LOWER(column_name))",
            "FROM information_schema.columns",
            "WHERE table_schema = #{schema}",
            "AND table_name IN",
            "<foreach collection='tableNames' item='tableName' open='(' separator=',' close=')'>",
            "#{tableName}",
            "</foreach>",
            "ORDER BY table_name, ordinal_position",
            "</script>"
    })
    List<String> findExistingColumnKeys(
            @Param("schema") String schema,
            @Param("tableNames") List<String> tableNames
    );
}
