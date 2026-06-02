package com.nuono.next.infrastructure.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CoreTableStatusMapper {

    @Select({
            "<script>",
            "SELECT table_name",
            "FROM information_schema.tables",
            "WHERE table_schema = #{schema}",
            "AND table_name IN",
            "<foreach collection='tableNames' item='tableName' open='(' separator=',' close=')'>",
            "#{tableName}",
            "</foreach>",
            "ORDER BY table_name",
            "</script>"
    })
    List<String> findExistingTableNames(
            @Param("schema") String schema,
            @Param("tableNames") List<String> tableNames
    );

    @Select({
            "<script>",
            "SELECT column_name",
            "FROM information_schema.columns",
            "WHERE table_schema = #{schema}",
            "AND table_name = #{tableName}",
            "AND column_name IN",
            "<foreach collection='columnNames' item='columnName' open='(' separator=',' close=')'>",
            "#{columnName}",
            "</foreach>",
            "ORDER BY column_name",
            "</script>"
    })
    List<String> findExistingColumnNames(
            @Param("schema") String schema,
            @Param("tableName") String tableName,
            @Param("columnNames") List<String> columnNames
    );
}
