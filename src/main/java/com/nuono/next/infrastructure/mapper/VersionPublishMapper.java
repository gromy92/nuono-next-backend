package com.nuono.next.infrastructure.mapper;

import com.nuono.next.versioning.VersionPublishAction;
import com.nuono.next.versioning.VersionPublishAuditLog;
import com.nuono.next.versioning.VersionPublishRecord;
import com.nuono.next.versioning.VersionPublishStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface VersionPublishMapper {

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
    int allocateVersionPublishId(IdSequenceCommand command);

    default Long nextVersionPublishId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateVersionPublishId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Version publish ID allocation failed: " + sequenceName);
        }
        return id;
    }

    default Long nextVersionPublishRecordId() {
        return nextVersionPublishId("version_publish_record", 80000L);
    }

    default Long nextVersionPublishAuditLogId() {
        return nextVersionPublishId("version_publish_audit_log", 81000L);
    }

    @Insert({
            "INSERT INTO version_publish_record (",
            "  id, domain_type, domain_ref_id, version_no, status, scope_summary, previous_version_id,",
            "  published_by, published_at, change_summary, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{record.id}, #{record.domainType}, #{record.domainRefId}, #{record.versionNo}, #{record.status},",
            "  #{record.scopeSummary}, #{record.previousVersionId}, #{record.publishedBy}, #{record.publishedAt},",
            "  #{record.changeSummary}, #{record.createdBy}, #{record.updatedBy}, #{record.createdAt}, #{record.updatedAt}",
            ")"
    })
    int insertRecord(@Param("record") VersionPublishRecord record);

    @Update({
            "UPDATE version_publish_record",
            "SET status = #{record.status},",
            "    scope_summary = #{record.scopeSummary},",
            "    previous_version_id = #{record.previousVersionId},",
            "    published_by = #{record.publishedBy},",
            "    published_at = #{record.publishedAt},",
            "    change_summary = #{record.changeSummary},",
            "    updated_by = #{record.updatedBy},",
            "    gmt_updated = #{record.updatedAt}",
            "WHERE id = #{record.id}"
    })
    int updateRecord(@Param("record") VersionPublishRecord record);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "domainType", javaType = String.class),
            @Arg(column = "domainRefId", javaType = Long.class),
            @Arg(column = "versionNo", javaType = String.class),
            @Arg(column = "status", javaType = VersionPublishStatus.class),
            @Arg(column = "scopeSummary", javaType = String.class),
            @Arg(column = "previousVersionId", javaType = Long.class),
            @Arg(column = "publishedBy", javaType = Long.class),
            @Arg(column = "publishedAt", javaType = LocalDateTime.class),
            @Arg(column = "changeSummary", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  domain_type AS domainType,",
            "  domain_ref_id AS domainRefId,",
            "  version_no AS versionNo,",
            "  status,",
            "  scope_summary AS scopeSummary,",
            "  previous_version_id AS previousVersionId,",
            "  published_by AS publishedBy,",
            "  published_at AS publishedAt,",
            "  change_summary AS changeSummary,",
            "  created_by AS createdBy,",
            "  updated_by AS updatedBy,",
            "  gmt_create AS createdAt,",
            "  gmt_updated AS updatedAt",
            "FROM version_publish_record",
            "WHERE id = #{id}"
    })
    VersionPublishRecord selectRecordById(@Param("id") Long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "domainType", javaType = String.class),
            @Arg(column = "domainRefId", javaType = Long.class),
            @Arg(column = "versionNo", javaType = String.class),
            @Arg(column = "status", javaType = VersionPublishStatus.class),
            @Arg(column = "scopeSummary", javaType = String.class),
            @Arg(column = "previousVersionId", javaType = Long.class),
            @Arg(column = "publishedBy", javaType = Long.class),
            @Arg(column = "publishedAt", javaType = LocalDateTime.class),
            @Arg(column = "changeSummary", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id, domain_type AS domainType, domain_ref_id AS domainRefId, version_no AS versionNo,",
            "  status, scope_summary AS scopeSummary, previous_version_id AS previousVersionId,",
            "  published_by AS publishedBy, published_at AS publishedAt, change_summary AS changeSummary,",
            "  created_by AS createdBy, updated_by AS updatedBy, gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM version_publish_record",
            "WHERE domain_type = #{domainType}",
            "  AND domain_ref_id = #{domainRefId}",
            "  AND status = 'PUBLISHED'",
            "ORDER BY published_at DESC, id DESC",
            "LIMIT 1"
    })
    VersionPublishRecord selectCurrent(
            @Param("domainType") String domainType,
            @Param("domainRefId") Long domainRefId
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "domainType", javaType = String.class),
            @Arg(column = "domainRefId", javaType = Long.class),
            @Arg(column = "versionNo", javaType = String.class),
            @Arg(column = "status", javaType = VersionPublishStatus.class),
            @Arg(column = "scopeSummary", javaType = String.class),
            @Arg(column = "previousVersionId", javaType = Long.class),
            @Arg(column = "publishedBy", javaType = Long.class),
            @Arg(column = "publishedAt", javaType = LocalDateTime.class),
            @Arg(column = "changeSummary", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id, domain_type AS domainType, domain_ref_id AS domainRefId, version_no AS versionNo,",
            "  status, scope_summary AS scopeSummary, previous_version_id AS previousVersionId,",
            "  published_by AS publishedBy, published_at AS publishedAt, change_summary AS changeSummary,",
            "  created_by AS createdBy, updated_by AS updatedBy, gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM version_publish_record",
            "WHERE domain_type = #{domainType}",
            "  AND domain_ref_id = #{domainRefId}",
            "ORDER BY gmt_create DESC, id DESC"
    })
    List<VersionPublishRecord> selectRecords(
            @Param("domainType") String domainType,
            @Param("domainRefId") Long domainRefId
    );

    @Insert({
            "INSERT INTO version_publish_audit_log (",
            "  id, publish_record_id, domain_type, domain_ref_id, action, operator_user_id, operator_role,",
            "  before_snapshot, after_snapshot, message, gmt_create",
            ") VALUES (",
            "  #{audit.id}, #{audit.publishRecordId}, #{audit.domainType}, #{audit.domainRefId}, #{audit.action},",
            "  #{audit.operatorUserId}, #{audit.operatorRole}, #{audit.beforeSnapshot}, #{audit.afterSnapshot},",
            "  #{audit.message}, #{audit.createdAt}",
            ")"
    })
    int insertAudit(@Param("audit") VersionPublishAuditLog auditLog);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "domainType", javaType = String.class),
            @Arg(column = "domainRefId", javaType = Long.class),
            @Arg(column = "action", javaType = VersionPublishAction.class),
            @Arg(column = "operatorUserId", javaType = Long.class),
            @Arg(column = "operatorRole", javaType = String.class),
            @Arg(column = "beforeSnapshot", javaType = String.class),
            @Arg(column = "afterSnapshot", javaType = String.class),
            @Arg(column = "message", javaType = String.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  publish_record_id AS publishRecordId,",
            "  domain_type AS domainType,",
            "  domain_ref_id AS domainRefId,",
            "  action,",
            "  operator_user_id AS operatorUserId,",
            "  operator_role AS operatorRole,",
            "  before_snapshot AS beforeSnapshot,",
            "  after_snapshot AS afterSnapshot,",
            "  message,",
            "  gmt_create AS createdAt",
            "FROM version_publish_audit_log",
            "WHERE domain_type = #{domainType}",
            "  AND domain_ref_id = #{domainRefId}",
            "ORDER BY id ASC"
    })
    List<VersionPublishAuditLog> selectAudit(
            @Param("domainType") String domainType,
            @Param("domainRefId") Long domainRefId
    );
}
