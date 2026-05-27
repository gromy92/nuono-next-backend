package com.nuono.next.infrastructure.mapper;

import com.nuono.next.operationsconfig.OperationConfigBundle;
import com.nuono.next.operationsconfig.OperationConfigBundleScopeStore;
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

public interface OperationConfigBundleMapper {

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
    int allocateOperationConfigBundleId(IdSequenceCommand command);

    default Long nextOperationConfigBundleId() {
        IdSequenceCommand command = new IdSequenceCommand("operation_config_bundle", 86000L);
        allocateOperationConfigBundleId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Operation config bundle ID allocation failed");
        }
        return id;
    }

    @Insert({
            "INSERT INTO operation_config_bundle (",
            "  id, publish_record_id, version_no, display_name, publish_source_role, publish_source_label,",
            "  scope_summary, affected_store_count, activity_rule_count, lifecycle_rule_summary,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{bundle.id}, #{bundle.publishRecordId}, #{bundle.versionNo}, #{bundle.displayName},",
            "  #{bundle.publishSourceRole}, #{bundle.publishSourceLabel}, #{bundle.scopeSummary},",
            "  #{bundle.affectedStoreCount}, #{bundle.activityRuleCount}, #{bundle.lifecycleRuleSummary},",
            "  #{bundle.createdBy}, #{bundle.updatedBy}, #{bundle.createdAt}, #{bundle.updatedAt}",
            ")"
    })
    int insertBundle(@Param("bundle") OperationConfigBundle bundle);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "versionNo", javaType = String.class),
            @Arg(column = "displayName", javaType = String.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "scopeSummary", javaType = String.class),
            @Arg(column = "affectedStoreCount", javaType = Integer.class),
            @Arg(column = "activityRuleCount", javaType = Integer.class),
            @Arg(column = "lifecycleRuleSummary", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id, publish_record_id AS publishRecordId, version_no AS versionNo, display_name AS displayName,",
            "  publish_source_role AS publishSourceRole, publish_source_label AS publishSourceLabel,",
            "  scope_summary AS scopeSummary, affected_store_count AS affectedStoreCount,",
            "  activity_rule_count AS activityRuleCount, lifecycle_rule_summary AS lifecycleRuleSummary,",
            "  created_by AS createdBy, updated_by AS updatedBy, gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM operation_config_bundle",
            "ORDER BY gmt_updated DESC, id DESC"
    })
    List<OperationConfigBundle> selectBundles();

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "versionNo", javaType = String.class),
            @Arg(column = "displayName", javaType = String.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "scopeSummary", javaType = String.class),
            @Arg(column = "affectedStoreCount", javaType = Integer.class),
            @Arg(column = "activityRuleCount", javaType = Integer.class),
            @Arg(column = "lifecycleRuleSummary", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id, publish_record_id AS publishRecordId, version_no AS versionNo, display_name AS displayName,",
            "  publish_source_role AS publishSourceRole, publish_source_label AS publishSourceLabel,",
            "  scope_summary AS scopeSummary, affected_store_count AS affectedStoreCount,",
            "  activity_rule_count AS activityRuleCount, lifecycle_rule_summary AS lifecycleRuleSummary,",
            "  created_by AS createdBy, updated_by AS updatedBy, gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM operation_config_bundle",
            "WHERE id = #{bundleId}"
    })
    OperationConfigBundle selectBundle(@Param("bundleId") Long bundleId);

    @Update({
            "UPDATE operation_config_bundle",
            "SET scope_summary = #{scopeSummary},",
            "    affected_store_count = #{affectedStoreCount},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{bundleId}"
    })
    int updateScopeSummary(
            @Param("bundleId") Long bundleId,
            @Param("scopeSummary") String scopeSummary,
            @Param("affectedStoreCount") Integer affectedStoreCount,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE operation_config_bundle",
            "SET activity_rule_count = #{activityRuleCount},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{bundleId}"
    })
    int updateActivityRuleCount(
            @Param("bundleId") Long bundleId,
            @Param("activityRuleCount") Integer activityRuleCount,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE operation_config_bundle",
            "SET lifecycle_rule_summary = #{lifecycleRuleSummary},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{bundleId}"
    })
    int updateLifecycleRuleSummary(
            @Param("bundleId") Long bundleId,
            @Param("lifecycleRuleSummary") String lifecycleRuleSummary,
            @Param("updatedBy") Long updatedBy
    );

    @Delete({
            "DELETE FROM operation_config_bundle_scope",
            "WHERE bundle_id = #{bundleId}"
    })
    int deleteScopeStores(@Param("bundleId") Long bundleId);

    @Insert({
            "INSERT INTO operation_config_bundle_scope (bundle_id, owner_user_id, store_code, site_code, gmt_create, gmt_updated)",
            "VALUES (#{bundleId}, #{scopeStore.ownerUserId}, #{scopeStore.storeCode}, #{scopeStore.siteCode}, NOW(), NOW())"
    })
    int insertScopeStore(
            @Param("bundleId") Long bundleId,
            @Param("scopeStore") OperationConfigBundleScopeStore scopeStore
    );

    @ConstructorArgs({
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class)
    })
    @Select({
            "SELECT owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode",
            "FROM operation_config_bundle_scope",
            "WHERE bundle_id = #{bundleId}",
            "ORDER BY owner_user_id, store_code, site_code"
    })
    List<OperationConfigBundleScopeStore> selectScopeStores(@Param("bundleId") Long bundleId);

    @Delete({
            "DELETE FROM operation_config_bundle",
            "WHERE id = #{bundleId}"
    })
    int deleteBundle(@Param("bundleId") Long bundleId);
}
