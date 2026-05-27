package com.nuono.next.infrastructure.mapper;

import com.nuono.next.operationsconfig.OperationConfigPublishStatus;
import com.nuono.next.operationsconfig.OperationLifecycleRule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface OperationLifecycleRuleMapper {

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
    int allocateOperationLifecycleRuleId(IdSequenceCommand command);

    default Long nextOperationLifecycleRuleId() {
        IdSequenceCommand command = new IdSequenceCommand("operation_lifecycle_rule", 83000L);
        allocateOperationLifecycleRuleId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Operation lifecycle rule ID allocation failed");
        }
        return id;
    }

    @Insert({
            "INSERT INTO operation_lifecycle_rule (",
            "  id, owner_user_id, store_code, site_code, rule_version, source_rule_version,",
            "  new_max_age_days, new_min_age_days, high_price_threshold,",
            "  growth_min_sales_growth_rate, growth_min_pv_growth_rate, growth_min_monthly_sales,",
            "  growth_min_active_sales_days, growth_max_volatility, stable_min_pv_growth_rate,",
            "  stable_volatility_min, stable_volatility_max, decline_max_volatility,",
            "  decline_max_sales_growth_rate, long_tail_max_volatility, long_tail_max_monthly_sales,",
            "  bundle_version_id, publish_record_id, publish_status, publish_source_role, publish_source_label,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{rule.id}, #{rule.ownerUserId}, #{rule.storeCode}, #{rule.siteCode}, #{rule.ruleVersion}, #{rule.sourceRuleVersion},",
            "  #{rule.newMaxAgeDays}, #{rule.newMinAgeDays}, #{rule.highPriceThreshold},",
            "  #{rule.growthMinSalesGrowthRate}, #{rule.growthMinPvGrowthRate}, #{rule.growthMinMonthlySales},",
            "  #{rule.growthMinActiveSalesDays}, #{rule.growthMaxVolatility}, #{rule.stableMinPvGrowthRate},",
            "  #{rule.stableVolatilityMin}, #{rule.stableVolatilityMax}, #{rule.declineMaxVolatility},",
            "  #{rule.declineMaxSalesGrowthRate}, #{rule.longTailMaxVolatility}, #{rule.longTailMaxMonthlySales},",
            "  #{rule.bundleVersionId}, #{rule.publishRecordId}, #{rule.publishStatus}, #{rule.publishSourceRole}, #{rule.publishSourceLabel},",
            "  #{rule.createdBy}, #{rule.updatedBy}, #{rule.createdAt}, #{rule.updatedAt}",
            ")"
    })
    int insertRule(@Param("rule") OperationLifecycleRule rule);

    @Update({
            "UPDATE operation_lifecycle_rule",
            "SET owner_user_id = #{rule.ownerUserId},",
            "    store_code = #{rule.storeCode},",
            "    site_code = #{rule.siteCode},",
            "    rule_version = #{rule.ruleVersion},",
            "    source_rule_version = #{rule.sourceRuleVersion},",
            "    new_max_age_days = #{rule.newMaxAgeDays},",
            "    new_min_age_days = #{rule.newMinAgeDays},",
            "    high_price_threshold = #{rule.highPriceThreshold},",
            "    growth_min_sales_growth_rate = #{rule.growthMinSalesGrowthRate},",
            "    growth_min_pv_growth_rate = #{rule.growthMinPvGrowthRate},",
            "    growth_min_monthly_sales = #{rule.growthMinMonthlySales},",
            "    growth_min_active_sales_days = #{rule.growthMinActiveSalesDays},",
            "    growth_max_volatility = #{rule.growthMaxVolatility},",
            "    stable_min_pv_growth_rate = #{rule.stableMinPvGrowthRate},",
            "    stable_volatility_min = #{rule.stableVolatilityMin},",
            "    stable_volatility_max = #{rule.stableVolatilityMax},",
            "    decline_max_volatility = #{rule.declineMaxVolatility},",
            "    decline_max_sales_growth_rate = #{rule.declineMaxSalesGrowthRate},",
            "    long_tail_max_volatility = #{rule.longTailMaxVolatility},",
            "    long_tail_max_monthly_sales = #{rule.longTailMaxMonthlySales},",
            "    bundle_version_id = #{rule.bundleVersionId},",
            "    publish_record_id = #{rule.publishRecordId},",
            "    publish_status = #{rule.publishStatus},",
            "    publish_source_role = #{rule.publishSourceRole},",
            "    publish_source_label = #{rule.publishSourceLabel},",
            "    updated_by = #{rule.updatedBy},",
            "    gmt_updated = #{rule.updatedAt}",
            "WHERE id = #{rule.id}"
    })
    int updateRule(@Param("rule") OperationLifecycleRule rule);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "ruleVersion", javaType = String.class),
            @Arg(column = "sourceRuleVersion", javaType = String.class),
            @Arg(column = "newMaxAgeDays", javaType = Integer.class),
            @Arg(column = "newMinAgeDays", javaType = Integer.class),
            @Arg(column = "highPriceThreshold", javaType = BigDecimal.class),
            @Arg(column = "growthMinSalesGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "growthMinPvGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "growthMinMonthlySales", javaType = BigDecimal.class),
            @Arg(column = "growthMinActiveSalesDays", javaType = Integer.class),
            @Arg(column = "growthMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "stableMinPvGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "stableVolatilityMin", javaType = BigDecimal.class),
            @Arg(column = "stableVolatilityMax", javaType = BigDecimal.class),
            @Arg(column = "declineMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "declineMaxSalesGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "longTailMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "longTailMaxMonthlySales", javaType = BigDecimal.class),
            @Arg(column = "bundleVersionId", javaType = Long.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "publishStatus", javaType = OperationConfigPublishStatus.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            SELECT_COLUMNS,
            "FROM operation_lifecycle_rule",
            "WHERE id = #{id}"
    })
    OperationLifecycleRule selectRuleById(@Param("id") Long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "ruleVersion", javaType = String.class),
            @Arg(column = "sourceRuleVersion", javaType = String.class),
            @Arg(column = "newMaxAgeDays", javaType = Integer.class),
            @Arg(column = "newMinAgeDays", javaType = Integer.class),
            @Arg(column = "highPriceThreshold", javaType = BigDecimal.class),
            @Arg(column = "growthMinSalesGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "growthMinPvGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "growthMinMonthlySales", javaType = BigDecimal.class),
            @Arg(column = "growthMinActiveSalesDays", javaType = Integer.class),
            @Arg(column = "growthMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "stableMinPvGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "stableVolatilityMin", javaType = BigDecimal.class),
            @Arg(column = "stableVolatilityMax", javaType = BigDecimal.class),
            @Arg(column = "declineMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "declineMaxSalesGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "longTailMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "longTailMaxMonthlySales", javaType = BigDecimal.class),
            @Arg(column = "bundleVersionId", javaType = Long.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "publishStatus", javaType = OperationConfigPublishStatus.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            SELECT_COLUMNS,
            "FROM operation_lifecycle_rule",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND (",
            "    (store_code = #{storeCode} AND site_code = #{siteCode})",
            "    OR (store_code = '*' AND site_code = '*' AND publish_status = 'PUBLISHED')",
            "  )",
            "  AND bundle_version_id IS NULL",
            "ORDER BY gmt_updated DESC, id DESC"
    })
    List<OperationLifecycleRule> selectRules(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "ruleVersion", javaType = String.class),
            @Arg(column = "sourceRuleVersion", javaType = String.class),
            @Arg(column = "newMaxAgeDays", javaType = Integer.class),
            @Arg(column = "newMinAgeDays", javaType = Integer.class),
            @Arg(column = "highPriceThreshold", javaType = BigDecimal.class),
            @Arg(column = "growthMinSalesGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "growthMinPvGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "growthMinMonthlySales", javaType = BigDecimal.class),
            @Arg(column = "growthMinActiveSalesDays", javaType = Integer.class),
            @Arg(column = "growthMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "stableMinPvGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "stableVolatilityMin", javaType = BigDecimal.class),
            @Arg(column = "stableVolatilityMax", javaType = BigDecimal.class),
            @Arg(column = "declineMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "declineMaxSalesGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "longTailMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "longTailMaxMonthlySales", javaType = BigDecimal.class),
            @Arg(column = "bundleVersionId", javaType = Long.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "publishStatus", javaType = OperationConfigPublishStatus.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            SELECT_COLUMNS,
            "FROM operation_lifecycle_rule",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND (",
            "    (store_code = #{storeCode} AND site_code = #{siteCode})",
            "    OR (store_code = '*' AND site_code = '*')",
            "  )",
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
            "ORDER BY CASE WHEN bundle_version_id IS NULL THEN 0 ELSE 1 END DESC,",
            "  CASE WHEN store_code = #{storeCode} AND site_code = #{siteCode} THEN 2 ELSE 1 END DESC,",
            "  gmt_updated DESC, id DESC LIMIT 1"
    })
    OperationLifecycleRule selectLatestPublished(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "ruleVersion", javaType = String.class),
            @Arg(column = "sourceRuleVersion", javaType = String.class),
            @Arg(column = "newMaxAgeDays", javaType = Integer.class),
            @Arg(column = "newMinAgeDays", javaType = Integer.class),
            @Arg(column = "highPriceThreshold", javaType = BigDecimal.class),
            @Arg(column = "growthMinSalesGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "growthMinPvGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "growthMinMonthlySales", javaType = BigDecimal.class),
            @Arg(column = "growthMinActiveSalesDays", javaType = Integer.class),
            @Arg(column = "growthMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "stableMinPvGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "stableVolatilityMin", javaType = BigDecimal.class),
            @Arg(column = "stableVolatilityMax", javaType = BigDecimal.class),
            @Arg(column = "declineMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "declineMaxSalesGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "longTailMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "longTailMaxMonthlySales", javaType = BigDecimal.class),
            @Arg(column = "bundleVersionId", javaType = Long.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "publishStatus", javaType = OperationConfigPublishStatus.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            SELECT_COLUMNS,
            "FROM operation_lifecycle_rule",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}",
            "  AND publish_status = 'DRAFT'",
            "  AND bundle_version_id IS NULL",
            "ORDER BY gmt_updated DESC, id DESC LIMIT 1"
    })
    OperationLifecycleRule selectLatestDraft(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "ruleVersion", javaType = String.class),
            @Arg(column = "sourceRuleVersion", javaType = String.class),
            @Arg(column = "newMaxAgeDays", javaType = Integer.class),
            @Arg(column = "newMinAgeDays", javaType = Integer.class),
            @Arg(column = "highPriceThreshold", javaType = BigDecimal.class),
            @Arg(column = "growthMinSalesGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "growthMinPvGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "growthMinMonthlySales", javaType = BigDecimal.class),
            @Arg(column = "growthMinActiveSalesDays", javaType = Integer.class),
            @Arg(column = "growthMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "stableMinPvGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "stableVolatilityMin", javaType = BigDecimal.class),
            @Arg(column = "stableVolatilityMax", javaType = BigDecimal.class),
            @Arg(column = "declineMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "declineMaxSalesGrowthRate", javaType = BigDecimal.class),
            @Arg(column = "longTailMaxVolatility", javaType = BigDecimal.class),
            @Arg(column = "longTailMaxMonthlySales", javaType = BigDecimal.class),
            @Arg(column = "bundleVersionId", javaType = Long.class),
            @Arg(column = "publishRecordId", javaType = Long.class),
            @Arg(column = "publishStatus", javaType = OperationConfigPublishStatus.class),
            @Arg(column = "publishSourceRole", javaType = String.class),
            @Arg(column = "publishSourceLabel", javaType = String.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class),
            @Arg(column = "createdAt", javaType = LocalDateTime.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            SELECT_COLUMNS,
            "FROM operation_lifecycle_rule",
            "WHERE bundle_version_id = #{bundleVersionId}",
            "ORDER BY gmt_updated DESC, id DESC"
    })
    List<OperationLifecycleRule> selectRulesByBundleVersion(@Param("bundleVersionId") Long bundleVersionId);

    @Select({
            "SELECT COUNT(*)",
            "FROM operation_lifecycle_rule",
            "WHERE bundle_version_id = #{bundleVersionId}"
    })
    int countRulesByBundleVersion(@Param("bundleVersionId") Long bundleVersionId);

    String SELECT_COLUMNS = "SELECT "
            + "id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode, "
            + "rule_version AS ruleVersion, source_rule_version AS sourceRuleVersion, "
            + "new_max_age_days AS newMaxAgeDays, new_min_age_days AS newMinAgeDays, "
            + "high_price_threshold AS highPriceThreshold, "
            + "growth_min_sales_growth_rate AS growthMinSalesGrowthRate, "
            + "growth_min_pv_growth_rate AS growthMinPvGrowthRate, "
            + "growth_min_monthly_sales AS growthMinMonthlySales, "
            + "growth_min_active_sales_days AS growthMinActiveSalesDays, "
            + "growth_max_volatility AS growthMaxVolatility, "
            + "stable_min_pv_growth_rate AS stableMinPvGrowthRate, "
            + "stable_volatility_min AS stableVolatilityMin, stable_volatility_max AS stableVolatilityMax, "
            + "decline_max_volatility AS declineMaxVolatility, "
            + "decline_max_sales_growth_rate AS declineMaxSalesGrowthRate, "
            + "long_tail_max_volatility AS longTailMaxVolatility, "
            + "long_tail_max_monthly_sales AS longTailMaxMonthlySales, "
            + "bundle_version_id AS bundleVersionId, "
            + "publish_record_id AS publishRecordId, publish_status AS publishStatus, "
            + "publish_source_role AS publishSourceRole, publish_source_label AS publishSourceLabel, "
            + "created_by AS createdBy, updated_by AS updatedBy, gmt_create AS createdAt, gmt_updated AS updatedAt ";
}
