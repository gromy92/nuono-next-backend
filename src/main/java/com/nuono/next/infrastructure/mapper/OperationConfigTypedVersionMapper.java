package com.nuono.next.infrastructure.mapper;

import com.nuono.next.operationsconfig.OperationConfigTypedVersion;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface OperationConfigTypedVersionMapper {

    @Insert({
            "INSERT INTO version_publish_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = {
                    "SELECT LAST_INSERT_ID()"
            },
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    int allocateOperationConfigTypedVersionId(IdSequenceCommand command);

    default Long nextOperationConfigTypedVersionId() {
        IdSequenceCommand command = new IdSequenceCommand("operation_config_typed_version", 88000L);
        allocateOperationConfigTypedVersionId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Operation config typed version ID allocation failed");
        }
        return id;
    }

    @Insert({
            "INSERT INTO operation_config_typed_version (",
            "  id, version_no, display_name, config_type, status, source_version_no, source_label,",
            "  summary, item_count, scope_summary, content_json, audit_json, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{version.id}, #{version.versionNo}, #{version.displayName}, #{version.configType}, #{version.status},",
            "  #{version.sourceVersionNo}, #{version.sourceLabel}, #{version.summary}, #{version.itemCount},",
            "  #{version.scopeSummary}, #{version.contentJson}, #{version.auditJson}, #{version.createdBy}, #{version.updatedBy},",
            "  #{version.createdAt}, #{version.updatedAt}",
            ")"
    })
    int insertVersion(@Param("version") OperationConfigTypedVersion version);

    @Update({
            "UPDATE operation_config_typed_version",
            "SET",
            "  display_name = #{version.displayName},",
            "  config_type = #{version.configType},",
            "  status = #{version.status},",
            "  source_version_no = #{version.sourceVersionNo},",
            "  source_label = #{version.sourceLabel},",
            "  summary = #{version.summary},",
            "  item_count = #{version.itemCount},",
            "  scope_summary = #{version.scopeSummary},",
            "  content_json = #{version.contentJson},",
            "  audit_json = #{version.auditJson},",
            "  updated_by = #{version.updatedBy},",
            "  gmt_updated = #{version.updatedAt}",
            "WHERE version_no = #{version.versionNo}"
    })
    int updateVersion(@Param("version") OperationConfigTypedVersion version);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "versionNo", javaType = String.class),
            @Arg(column = "displayName", javaType = String.class),
            @Arg(column = "configType", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "sourceVersionNo", javaType = String.class),
            @Arg(column = "sourceLabel", javaType = String.class),
            @Arg(column = "summary", javaType = String.class),
            @Arg(column = "itemCount", javaType = int.class),
            @Arg(column = "scopeSummary", javaType = String.class),
            @Arg(column = "contentJson", javaType = String.class),
            @Arg(column = "auditJson", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id, version_no AS versionNo, display_name AS displayName, config_type AS configType, status,",
            "  source_version_no AS sourceVersionNo, source_label AS sourceLabel, summary, item_count AS itemCount,",
            "  scope_summary AS scopeSummary, content_json AS contentJson, audit_json AS auditJson, created_by AS createdBy, updated_by AS updatedBy,",
            "  gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM operation_config_typed_version",
            "ORDER BY gmt_updated DESC, id DESC"
    })
    List<OperationConfigTypedVersion> selectVersions();

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "versionNo", javaType = String.class),
            @Arg(column = "displayName", javaType = String.class),
            @Arg(column = "configType", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "sourceVersionNo", javaType = String.class),
            @Arg(column = "sourceLabel", javaType = String.class),
            @Arg(column = "summary", javaType = String.class),
            @Arg(column = "itemCount", javaType = int.class),
            @Arg(column = "scopeSummary", javaType = String.class),
            @Arg(column = "contentJson", javaType = String.class),
            @Arg(column = "auditJson", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id, version_no AS versionNo, display_name AS displayName, config_type AS configType, status,",
            "  source_version_no AS sourceVersionNo, source_label AS sourceLabel, summary, item_count AS itemCount,",
            "  scope_summary AS scopeSummary, content_json AS contentJson, audit_json AS auditJson, created_by AS createdBy, updated_by AS updatedBy,",
            "  gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM operation_config_typed_version",
            "WHERE version_no = #{versionNo}"
    })
    OperationConfigTypedVersion selectVersionByVersionNo(@Param("versionNo") String versionNo);

    @Delete({
            "DELETE FROM operation_config_typed_version",
            "WHERE version_no = #{versionNo}"
    })
    int deleteVersionByVersionNo(@Param("versionNo") String versionNo);
}
