package com.nuono.next.infrastructure.mapper;

import com.nuono.next.operationsconfig.OperationCalendarRule;
import com.nuono.next.operationsconfig.OperationConfigPublishStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface OperationCalendarRuleMapper {

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
    int allocateOperationCalendarRuleId(IdSequenceCommand command);

    default Long nextOperationCalendarRuleId() {
        IdSequenceCommand command = new IdSequenceCommand("operation_calendar_rule", 82000L);
        allocateOperationCalendarRuleId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Operation calendar rule ID allocation failed");
        }
        return id;
    }

    @Insert({
            "INSERT INTO operation_calendar_rule (",
            "  id, owner_user_id, store_code, site_code, rule_name, activity_type, date_from, date_to,",
            "  recurring_expression, target_scope_type, target_scope_value, factor_value, factor_purpose, enabled,",
            "  bundle_version_id, publish_record_id, publish_status, publish_source_role, publish_source_label,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{rule.id}, #{rule.ownerUserId}, #{rule.storeCode}, #{rule.siteCode}, #{rule.ruleName},",
            "  #{rule.activityType}, #{rule.dateFrom}, #{rule.dateTo}, #{rule.recurringExpression},",
            "  #{rule.targetScopeType}, #{rule.targetScopeValue}, #{rule.factorValue}, #{rule.factorPurpose},",
            "  #{rule.enabled}, #{rule.bundleVersionId}, #{rule.publishRecordId}, #{rule.publishStatus},",
            "  #{rule.publishSourceRole}, #{rule.publishSourceLabel}, #{rule.createdBy}, #{rule.updatedBy},",
            "  #{rule.createdAt}, #{rule.updatedAt}",
            ")"
    })
    int insertRule(@Param("rule") OperationCalendarRule rule);

    @Update({
            "UPDATE operation_calendar_rule",
            "SET owner_user_id = #{rule.ownerUserId},",
            "    store_code = #{rule.storeCode},",
            "    site_code = #{rule.siteCode},",
            "    rule_name = #{rule.ruleName},",
            "    activity_type = #{rule.activityType},",
            "    date_from = #{rule.dateFrom},",
            "    date_to = #{rule.dateTo},",
            "    recurring_expression = #{rule.recurringExpression},",
            "    target_scope_type = #{rule.targetScopeType},",
            "    target_scope_value = #{rule.targetScopeValue},",
            "    factor_value = #{rule.factorValue},",
            "    factor_purpose = #{rule.factorPurpose},",
            "    enabled = #{rule.enabled},",
            "    bundle_version_id = #{rule.bundleVersionId},",
            "    publish_record_id = #{rule.publishRecordId},",
            "    publish_status = #{rule.publishStatus},",
            "    publish_source_role = #{rule.publishSourceRole},",
            "    publish_source_label = #{rule.publishSourceLabel},",
            "    updated_by = #{rule.updatedBy},",
            "    gmt_updated = #{rule.updatedAt}",
            "WHERE id = #{rule.id}"
    })
    int updateRule(@Param("rule") OperationCalendarRule rule);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "ruleName", javaType = String.class),
            @Arg(column = "activityType", javaType = String.class),
            @Arg(column = "dateFrom", javaType = LocalDate.class),
            @Arg(column = "dateTo", javaType = LocalDate.class),
            @Arg(column = "recurringExpression", javaType = String.class),
            @Arg(column = "targetScopeType", javaType = String.class),
            @Arg(column = "targetScopeValue", javaType = String.class),
            @Arg(column = "factorValue", javaType = BigDecimal.class),
            @Arg(column = "factorPurpose", javaType = String.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "publishStatus", javaType = OperationConfigPublishStatus.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class),
            @Arg(column = "bundleVersionId", javaType = Long.class)
    })
    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  rule_name AS ruleName, activity_type AS activityType, date_from AS dateFrom, date_to AS dateTo,",
            "  recurring_expression AS recurringExpression, target_scope_type AS targetScopeType,",
            "  target_scope_value AS targetScopeValue, factor_value AS factorValue, factor_purpose AS factorPurpose,",
            "  enabled, bundle_version_id AS bundleVersionId, publish_record_id AS publishRecordId, publish_status AS publishStatus,",
            "  publish_source_role AS publishSourceRole, publish_source_label AS publishSourceLabel,",
            "  created_by AS createdBy, updated_by AS updatedBy, gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM operation_calendar_rule",
            "WHERE id = #{id}"
    })
    OperationCalendarRule selectRuleById(@Param("id") Long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "ruleName", javaType = String.class),
            @Arg(column = "activityType", javaType = String.class),
            @Arg(column = "dateFrom", javaType = LocalDate.class),
            @Arg(column = "dateTo", javaType = LocalDate.class),
            @Arg(column = "recurringExpression", javaType = String.class),
            @Arg(column = "targetScopeType", javaType = String.class),
            @Arg(column = "targetScopeValue", javaType = String.class),
            @Arg(column = "factorValue", javaType = BigDecimal.class),
            @Arg(column = "factorPurpose", javaType = String.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "publishStatus", javaType = OperationConfigPublishStatus.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class),
            @Arg(column = "bundleVersionId", javaType = Long.class)
    })
    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  rule_name AS ruleName, activity_type AS activityType, date_from AS dateFrom, date_to AS dateTo,",
            "  recurring_expression AS recurringExpression, target_scope_type AS targetScopeType,",
            "  target_scope_value AS targetScopeValue, factor_value AS factorValue, factor_purpose AS factorPurpose,",
            "  enabled, bundle_version_id AS bundleVersionId, publish_record_id AS publishRecordId, publish_status AS publishStatus,",
            "  publish_source_role AS publishSourceRole, publish_source_label AS publishSourceLabel,",
            "  created_by AS createdBy, updated_by AS updatedBy, gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM operation_calendar_rule",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND (",
            "    (store_code = #{storeCode} AND site_code = #{siteCode})",
            "    OR (store_code = '*' AND site_code = '*')",
            "  )",
            "  AND enabled = 1",
            "  AND (",
            "    (bundle_version_id IS NULL AND publish_status = 'PUBLISHED')",
            "    OR bundle_version_id IN (",
            "      SELECT current_bundle.bundle_id",
            "      FROM (",
            "        SELECT b.id AS bundle_id",
            "        FROM operation_config_bundle b",
            "        JOIN version_publish_record v ON v.id = b.publish_record_id AND v.status = 'PUBLISHED'",
            "        JOIN operation_config_bundle_scope s ON s.bundle_id = b.id",
            "        WHERE s.owner_user_id = #{ownerUserId}",
            "          AND (",
            "            (s.store_code = #{storeCode} AND s.site_code = #{siteCode})",
            "            OR (s.store_code = '*' AND s.site_code = '*')",
            "          )",
            "        ORDER BY CASE",
            "          WHEN s.store_code = #{storeCode} AND s.site_code = #{siteCode} THEN 2",
            "          WHEN s.store_code = '*' AND s.site_code = '*' THEN 1",
            "          ELSE 0",
            "        END DESC, v.published_at DESC, b.id DESC",
            "        LIMIT 1",
            "      ) current_bundle",
            "    )",
            "  )",
            "ORDER BY date_from ASC, id ASC"
    })
    List<OperationCalendarRule> selectActiveRules(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "ruleName", javaType = String.class),
            @Arg(column = "activityType", javaType = String.class),
            @Arg(column = "dateFrom", javaType = LocalDate.class),
            @Arg(column = "dateTo", javaType = LocalDate.class),
            @Arg(column = "recurringExpression", javaType = String.class),
            @Arg(column = "targetScopeType", javaType = String.class),
            @Arg(column = "targetScopeValue", javaType = String.class),
            @Arg(column = "factorValue", javaType = BigDecimal.class),
            @Arg(column = "factorPurpose", javaType = String.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "publishStatus", javaType = OperationConfigPublishStatus.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class),
            @Arg(column = "bundleVersionId", javaType = Long.class)
    })
    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  rule_name AS ruleName, activity_type AS activityType, date_from AS dateFrom, date_to AS dateTo,",
            "  recurring_expression AS recurringExpression, target_scope_type AS targetScopeType,",
            "  target_scope_value AS targetScopeValue, factor_value AS factorValue, factor_purpose AS factorPurpose,",
            "  enabled, bundle_version_id AS bundleVersionId, publish_record_id AS publishRecordId, publish_status AS publishStatus,",
            "  publish_source_role AS publishSourceRole, publish_source_label AS publishSourceLabel,",
            "  created_by AS createdBy, updated_by AS updatedBy, gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM operation_calendar_rule",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code IN (#{storeCode}, '*')",
            "  AND site_code IN (#{siteCode}, '*')",
            "  AND enabled = 1",
            "  AND (",
            "    (bundle_version_id IS NULL AND publish_status = 'PUBLISHED')",
            "    OR bundle_version_id IN (",
            "      SELECT current_bundle.bundle_id",
            "      FROM (",
            "        SELECT b.id AS bundle_id",
            "        FROM operation_config_bundle b",
            "        JOIN version_publish_record v ON v.id = b.publish_record_id AND v.status = 'PUBLISHED'",
            "        JOIN operation_config_bundle_scope s ON s.bundle_id = b.id",
            "        WHERE s.owner_user_id = #{ownerUserId}",
            "          AND (",
            "            (s.store_code = #{storeCode} AND s.site_code = #{siteCode})",
            "            OR (s.store_code = '*' AND s.site_code = '*')",
            "          )",
            "        ORDER BY CASE",
            "          WHEN s.store_code = #{storeCode} AND s.site_code = #{siteCode} THEN 2",
            "          WHEN s.store_code = '*' AND s.site_code = '*' THEN 1",
            "          ELSE 0",
            "        END DESC, v.published_at DESC, b.id DESC",
            "        LIMIT 1",
            "      ) current_bundle",
            "    )",
            "  )",
            "ORDER BY date_from ASC, id ASC"
    })
    List<OperationCalendarRule> selectActiveRulesForFactorResolution(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "ruleName", javaType = String.class),
            @Arg(column = "activityType", javaType = String.class),
            @Arg(column = "dateFrom", javaType = LocalDate.class),
            @Arg(column = "dateTo", javaType = LocalDate.class),
            @Arg(column = "recurringExpression", javaType = String.class),
            @Arg(column = "targetScopeType", javaType = String.class),
            @Arg(column = "targetScopeValue", javaType = String.class),
            @Arg(column = "factorValue", javaType = BigDecimal.class),
            @Arg(column = "factorPurpose", javaType = String.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "publishStatus", javaType = OperationConfigPublishStatus.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class),
            @Arg(column = "bundleVersionId", javaType = Long.class)
    })
    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  rule_name AS ruleName, activity_type AS activityType, date_from AS dateFrom, date_to AS dateTo,",
            "  recurring_expression AS recurringExpression, target_scope_type AS targetScopeType,",
            "  target_scope_value AS targetScopeValue, factor_value AS factorValue, factor_purpose AS factorPurpose,",
            "  enabled, bundle_version_id AS bundleVersionId, publish_record_id AS publishRecordId, publish_status AS publishStatus,",
            "  publish_source_role AS publishSourceRole, publish_source_label AS publishSourceLabel,",
            "  created_by AS createdBy, updated_by AS updatedBy, gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM operation_calendar_rule",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND (",
            "    (store_code = #{storeCode} AND site_code = #{siteCode})",
            "    OR (store_code = '*' AND site_code = '*' AND publish_status = 'PUBLISHED')",
            "  )",
            "ORDER BY date_from DESC, id DESC"
    })
    List<OperationCalendarRule> selectRules(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "ruleName", javaType = String.class),
            @Arg(column = "activityType", javaType = String.class),
            @Arg(column = "dateFrom", javaType = LocalDate.class),
            @Arg(column = "dateTo", javaType = LocalDate.class),
            @Arg(column = "recurringExpression", javaType = String.class),
            @Arg(column = "targetScopeType", javaType = String.class),
            @Arg(column = "targetScopeValue", javaType = String.class),
            @Arg(column = "factorValue", javaType = BigDecimal.class),
            @Arg(column = "factorPurpose", javaType = String.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "publishStatus", javaType = OperationConfigPublishStatus.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class),
            @Arg(column = "bundleVersionId", javaType = Long.class)
    })
    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  rule_name AS ruleName, activity_type AS activityType, date_from AS dateFrom, date_to AS dateTo,",
            "  recurring_expression AS recurringExpression, target_scope_type AS targetScopeType,",
            "  target_scope_value AS targetScopeValue, factor_value AS factorValue, factor_purpose AS factorPurpose,",
            "  enabled, bundle_version_id AS bundleVersionId, publish_record_id AS publishRecordId, publish_status AS publishStatus,",
            "  publish_source_role AS publishSourceRole, publish_source_label AS publishSourceLabel,",
            "  created_by AS createdBy, updated_by AS updatedBy, gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM operation_calendar_rule",
            "WHERE bundle_version_id = #{bundleVersionId}",
            "ORDER BY date_from DESC, id DESC"
    })
    List<OperationCalendarRule> selectRulesByBundleVersion(@Param("bundleVersionId") Long bundleVersionId);

    @Select({
            "SELECT COUNT(*)",
            "FROM operation_calendar_rule",
            "WHERE bundle_version_id = #{bundleVersionId}"
    })
    int countRulesByBundleVersion(@Param("bundleVersionId") Long bundleVersionId);
}
