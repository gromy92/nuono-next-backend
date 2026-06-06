package com.nuono.next.infrastructure.mapper;

import com.nuono.next.outboundfee.OutboundFeeCalculationPolicyFact;
import com.nuono.next.outboundfee.OutboundFeeWeightSlabRuleFact;
import com.nuono.next.outboundfee.OfficialOutboundFeeCalculationFact;
import com.nuono.next.outboundfee.OfficialOutboundFeeCalculationView;
import com.nuono.next.outboundfee.OutboundSizeClassificationRuleFact;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface OfficialOutboundFeeMapper {

    @Select({
            "SELECT COALESCE(MAX(id), #{initialValue}) + 1",
            "FROM ${tableName}"
    })
    Long nextFactId(@Param("tableName") String tableName, @Param("initialValue") long initialValue);

    @Insert({
            "INSERT INTO official_outbound_fee_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    void nextId(IdSequenceCommand command);

    default Long nextCalculationFactId() {
        IdSequenceCommand command = new IdSequenceCommand("outbound_fee_calculation_fact", 720000L);
        nextId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Official outbound fee calculation fact ID allocation failed.");
        }
        return id;
    }

    @Select({
            "SELECT COUNT(1)",
            "FROM ${tableName}",
            "WHERE source_version_item_id = #{sourceVersionItemId}"
    })
    int countBySourceVersionItemId(
            @Param("tableName") String tableName,
            @Param("sourceVersionItemId") Long sourceVersionItemId
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM ${tableName}",
            "WHERE natural_key = #{naturalKey}",
            "  AND status = 'ACTIVE'"
    })
    int countActiveByNaturalKey(
            @Param("tableName") String tableName,
            @Param("naturalKey") String naturalKey
    );

    @Update({
            "UPDATE ${tableName}",
            "SET status = 'SUPERSEDED',",
            "    gmt_updated = NOW()",
            "WHERE natural_key = #{naturalKey}",
            "  AND status = 'ACTIVE'"
    })
    int supersedeActiveByNaturalKey(
            @Param("tableName") String tableName,
            @Param("naturalKey") String naturalKey
    );

    @Update({
            "<script>",
            "UPDATE ${tableName}",
            "SET status = 'SUPERSEDED',",
            "    gmt_updated = NOW()",
            "WHERE country = #{country}",
            "  AND platform = #{platform}",
            "  AND fulfillment_type = #{fulfillmentType}",
            "  AND status = 'ACTIVE'",
            "  AND natural_key NOT IN",
            "  <foreach collection='snapshotNaturalKeys' item='naturalKey' open='(' separator=',' close=')'>",
            "    #{naturalKey}",
            "  </foreach>",
            "</script>"
    })
    int supersedeActiveMissingFromSnapshot(
            @Param("tableName") String tableName,
            @Param("country") String country,
            @Param("platform") String platform,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("snapshotNaturalKeys") List<String> snapshotNaturalKeys
    );

    @Select({
            "SELECT",
            "  vi.natural_key AS naturalKey,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.country')) AS country,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.platform')) AS platform,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.fulfillmentType')) AS fulfillmentType,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.classificationName')) AS classificationName,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.longestSideMaxCm')) AS longestSideMaxCm,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.medianSideMaxCm')) AS medianSideMaxCm,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.shortestSideMaxCm')) AS shortestSideMaxCm,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.maxShippingWeightGrams')) AS maxShippingWeightGrams,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.packagingWeightGrams')) AS packagingWeightGrams,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.priority')) AS priority,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.dimensionUnit')) AS dimensionUnit,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.weightUnit')) AS weightUnit,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')) AS effectiveFrom,",
            "  'ACTIVE' AS status,",
            "  'file_management' AS sourceType,",
            "  v.source_task_id AS sourceTaskId,",
            "  v.source_result_id AS sourceResultId,",
            "  vi.version_id AS sourceVersionId,",
            "  vi.id AS sourceVersionItemId,",
            "  v.version_no AS sourceFileName,",
            "  CONCAT('version_item:', vi.id) AS sourceLocator",
            "FROM file_mgmt_parse_active_version av",
            "JOIN file_mgmt_parse_version v",
            "  ON v.id = av.version_id",
            " AND v.is_deleted = b'0'",
            " AND v.version_status = 'active'",
            "JOIN file_mgmt_parse_version_item vi",
            "  ON vi.version_id = av.version_id",
            " AND vi.target_plan_id = av.target_plan_id",
            " AND vi.is_deleted = b'0'",
            "WHERE av.is_deleted = b'0'",
            "  AND vi.item_type = 'outbound_size_classification_rule'",
            "  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.country'))) = UPPER(#{country})",
            "  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.platform'))) = UPPER(#{platform})",
            "  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.fulfillmentType'))) = UPPER(#{fulfillmentType})",
            "  AND (#{calculationDate} IS NULL",
            "       OR NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), '') IS NULL",
            "       OR NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), '') <= DATE_FORMAT(#{calculationDate}, '%Y-%m-%d'))",
            "ORDER BY COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), ''), '1970-01-01') DESC,",
            "         COALESCE(CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.priority')), '') AS UNSIGNED), 2147483647) ASC,",
            "         vi.id ASC"
    })
    List<Map<String, Object>> selectActiveSizeClassificationRows(
            @Param("country") String country,
            @Param("platform") String platform,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("calculationDate") LocalDate calculationDate
    );

    @Select({
            "SELECT",
            "  vi.natural_key AS naturalKey,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.country')) AS country,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.platform')) AS platform,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.fulfillmentType')) AS fulfillmentType,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.classificationName')) AS classificationName,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.weightMinGrams')) AS weightMinGrams,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.weightMinInclusive')) AS weightMinInclusive,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.weightMaxGrams')) AS weightMaxGrams,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.weightMaxInclusive')) AS weightMaxInclusive,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.standardFeeAmount')) AS standardFeeAmount,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.highAspFeeAmount')) AS highAspFeeAmount,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.salesPriceThresholdAmount')) AS salesPriceThresholdAmount,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.thresholdCurrency')) AS thresholdCurrency,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.extraWeightStepGrams')) AS extraWeightStepGrams,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.extraFeeAmount')) AS extraFeeAmount,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.currency')) AS currency,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')) AS effectiveFrom,",
            "  'ACTIVE' AS status,",
            "  'file_management' AS sourceType,",
            "  v.source_task_id AS sourceTaskId,",
            "  v.source_result_id AS sourceResultId,",
            "  vi.version_id AS sourceVersionId,",
            "  vi.id AS sourceVersionItemId,",
            "  v.version_no AS sourceFileName,",
            "  CONCAT('version_item:', vi.id) AS sourceLocator",
            "FROM file_mgmt_parse_active_version av",
            "JOIN file_mgmt_parse_version v",
            "  ON v.id = av.version_id",
            " AND v.is_deleted = b'0'",
            " AND v.version_status = 'active'",
            "JOIN file_mgmt_parse_version_item vi",
            "  ON vi.version_id = av.version_id",
            " AND vi.target_plan_id = av.target_plan_id",
            " AND vi.is_deleted = b'0'",
            "WHERE av.is_deleted = b'0'",
            "  AND vi.item_type = 'outbound_fee_weight_slab_rule'",
            "  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.country'))) = UPPER(#{country})",
            "  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.platform'))) = UPPER(#{platform})",
            "  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.fulfillmentType'))) = UPPER(#{fulfillmentType})",
            "  AND (#{calculationDate} IS NULL",
            "       OR NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), '') IS NULL",
            "       OR NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), '') <= DATE_FORMAT(#{calculationDate}, '%Y-%m-%d'))",
            "ORDER BY COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), ''), '1970-01-01') DESC,",
            "         COALESCE(CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.weightMaxGrams')), '') AS DECIMAL(18,6)), 999999999999) ASC,",
            "         vi.id ASC"
    })
    List<Map<String, Object>> selectActiveWeightSlabRows(
            @Param("country") String country,
            @Param("platform") String platform,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("calculationDate") LocalDate calculationDate
    );

    @Select({
            "SELECT",
            "  vi.natural_key AS naturalKey,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.country')) AS country,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.platform')) AS platform,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.fulfillmentType')) AS fulfillmentType,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.policyName')) AS policyName,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.shippingWeightFormula')) AS shippingWeightFormula,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.dimensionSortRule')) AS dimensionSortRule,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.weightBoundaryRule')) AS weightBoundaryRule,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.roundingRule')) AS roundingRule,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.salesPriceThresholdAmount')) AS salesPriceThresholdAmount,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.thresholdCurrency')) AS thresholdCurrency,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.dimensionUnit')) AS dimensionUnit,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.weightUnit')) AS weightUnit,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')) AS effectiveFrom,",
            "  'ACTIVE' AS status,",
            "  'file_management' AS sourceType,",
            "  v.source_task_id AS sourceTaskId,",
            "  v.source_result_id AS sourceResultId,",
            "  vi.version_id AS sourceVersionId,",
            "  vi.id AS sourceVersionItemId,",
            "  v.version_no AS sourceFileName,",
            "  CONCAT('version_item:', vi.id) AS sourceLocator",
            "FROM file_mgmt_parse_active_version av",
            "JOIN file_mgmt_parse_version v",
            "  ON v.id = av.version_id",
            " AND v.is_deleted = b'0'",
            " AND v.version_status = 'active'",
            "JOIN file_mgmt_parse_version_item vi",
            "  ON vi.version_id = av.version_id",
            " AND vi.target_plan_id = av.target_plan_id",
            " AND vi.is_deleted = b'0'",
            "WHERE av.is_deleted = b'0'",
            "  AND vi.item_type = 'outbound_fee_calculation_policy'",
            "  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.country'))) = UPPER(#{country})",
            "  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.platform'))) = UPPER(#{platform})",
            "  AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.fulfillmentType'))) = UPPER(#{fulfillmentType})",
            "  AND (#{calculationDate} IS NULL",
            "       OR NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), '') IS NULL",
            "       OR NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), '') <= DATE_FORMAT(#{calculationDate}, '%Y-%m-%d'))",
            "ORDER BY COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), ''), '1970-01-01') DESC,",
            "         vi.id ASC",
            "LIMIT 1"
    })
    List<Map<String, Object>> selectActiveCalculationPolicyRows(
            @Param("country") String country,
            @Param("platform") String platform,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("calculationDate") LocalDate calculationDate
    );

    @Insert({
            "INSERT INTO official_outbound_size_classification_rule (",
            "  id, natural_key, country, platform, fulfillment_type, classification_name,",
            "  longest_side_max_cm, median_side_max_cm, shortest_side_max_cm, max_shipping_weight_grams,",
            "  packaging_weight_grams, priority, dimension_unit, weight_unit, effective_from,",
            "  source_type, source_task_id, source_result_id, source_version_id, source_version_item_id,",
            "  source_file_name, source_locator, status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.naturalKey}, #{fact.country}, #{fact.platform}, #{fact.fulfillmentType}, #{fact.classificationName},",
            "  #{fact.longestSideMaxCm}, #{fact.medianSideMaxCm}, #{fact.shortestSideMaxCm}, #{fact.maxShippingWeightGrams},",
            "  #{fact.packagingWeightGrams}, #{fact.priority}, #{fact.dimensionUnit}, #{fact.weightUnit}, #{fact.effectiveFrom},",
            "  #{fact.sourceLineage.sourceType}, #{fact.sourceLineage.sourceTaskId}, #{fact.sourceLineage.sourceResultId},",
            "  #{fact.sourceLineage.sourceVersionId}, #{fact.sourceLineage.sourceVersionItemId},",
            "  #{fact.sourceLineage.sourceFileName}, #{fact.sourceLineage.sourceLocator}, #{fact.status},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertSizeClassification(
            @Param("id") Long id,
            @Param("fact") OutboundSizeClassificationRuleFact fact,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO official_outbound_fee_weight_slab_rule (",
            "  id, natural_key, country, platform, fulfillment_type, classification_name,",
            "  weight_min_grams, weight_min_inclusive, weight_max_grams, weight_max_inclusive,",
            "  standard_fee_amount, high_asp_fee_amount, sales_price_threshold_amount, threshold_currency,",
            "  extra_weight_step_grams, extra_fee_amount, currency, effective_from,",
            "  source_type, source_task_id, source_result_id, source_version_id, source_version_item_id,",
            "  source_file_name, source_locator, status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.naturalKey}, #{fact.country}, #{fact.platform}, #{fact.fulfillmentType}, #{fact.classificationName},",
            "  #{fact.weightMinGrams}, #{fact.weightMinInclusive}, #{fact.weightMaxGrams}, #{fact.weightMaxInclusive},",
            "  #{fact.standardFeeAmount}, #{fact.highAspFeeAmount}, #{fact.salesPriceThresholdAmount}, #{fact.thresholdCurrency},",
            "  #{fact.extraWeightStepGrams}, #{fact.extraFeeAmount}, #{fact.currency}, #{fact.effectiveFrom},",
            "  #{fact.sourceLineage.sourceType}, #{fact.sourceLineage.sourceTaskId}, #{fact.sourceLineage.sourceResultId},",
            "  #{fact.sourceLineage.sourceVersionId}, #{fact.sourceLineage.sourceVersionItemId},",
            "  #{fact.sourceLineage.sourceFileName}, #{fact.sourceLineage.sourceLocator}, #{fact.status},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertWeightSlab(
            @Param("id") Long id,
            @Param("fact") OutboundFeeWeightSlabRuleFact fact,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO official_outbound_fee_calculation_policy (",
            "  id, natural_key, country, platform, fulfillment_type, policy_name, shipping_weight_formula,",
            "  dimension_sort_rule, weight_boundary_rule, rounding_rule, sales_price_threshold_amount,",
            "  threshold_currency, dimension_unit, weight_unit, effective_from,",
            "  source_type, source_task_id, source_result_id, source_version_id, source_version_item_id,",
            "  source_file_name, source_locator, status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.naturalKey}, #{fact.country}, #{fact.platform}, #{fact.fulfillmentType}, #{fact.policyName},",
            "  #{fact.shippingWeightFormula}, #{fact.dimensionSortRule}, #{fact.weightBoundaryRule}, #{fact.roundingRule},",
            "  #{fact.salesPriceThresholdAmount}, #{fact.thresholdCurrency}, #{fact.dimensionUnit}, #{fact.weightUnit}, #{fact.effectiveFrom},",
            "  #{fact.sourceLineage.sourceType}, #{fact.sourceLineage.sourceTaskId}, #{fact.sourceLineage.sourceResultId},",
            "  #{fact.sourceLineage.sourceVersionId}, #{fact.sourceLineage.sourceVersionItemId},",
            "  #{fact.sourceLineage.sourceFileName}, #{fact.sourceLineage.sourceLocator}, #{fact.status},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertCalculationPolicy(
            @Param("id") Long id,
            @Param("fact") OutboundFeeCalculationPolicyFact fact,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO official_outbound_fee_calculation_fact (",
            "  id, owner_user_id, store_code, site, country, platform, fulfillment_type, variant_id,",
            "  sku_id, partner_sku, child_sku,",
            "  effective_source_id, effective_source_type,",
            "  product_length_cm, product_width_cm, product_height_cm, product_weight_g,",
            "  sale_price, market_currency, calculation_date, fee_amount, currency, tax_multiplier, tax_included_fee_amount,",
            "  matched_classification_name, matched_slab_natural_key, source_version_id, evidence_json,",
            "  status, failure_code, message, calculated_at, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.ownerUserId}, #{fact.storeCode}, #{fact.site}, #{fact.country}, #{fact.platform}, #{fact.fulfillmentType}, #{fact.variantId},",
            "  #{fact.skuId}, #{fact.partnerSku}, #{fact.childSku},",
            "  #{fact.effectiveSourceId}, #{fact.effectiveSourceType},",
            "  #{fact.productLengthCm}, #{fact.productWidthCm}, #{fact.productHeightCm}, #{fact.productWeightG},",
            "  #{fact.salePrice}, #{fact.marketCurrency}, #{fact.calculationDate}, #{fact.feeAmount}, #{fact.currency}, #{fact.taxMultiplier}, #{fact.taxIncludedFeeAmount},",
            "  #{fact.matchedClassificationName}, #{fact.matchedSlabNaturalKey}, #{fact.sourceVersionId}, #{fact.evidenceJson},",
            "  #{fact.status}, #{fact.failureCode}, #{fact.message}, NOW(), #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertCalculationFact(
            @Param("id") Long id,
            @Param("fact") OfficialOutboundFeeCalculationFact fact,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "<script>",
            "SELECT",
            "  latest.id AS calculationFactId,",
            "  latest.owner_user_id AS ownerUserId,",
            "  latest.store_code AS storeCode,",
            "  COALESCE(latest.sku_id, sku_match.skuId) AS skuId,",
            "  latest.variant_id AS variantId,",
            "  latest.partner_sku AS partnerSku,",
            "  latest.child_sku AS childSku,",
            "  latest.site,",
            "  latest.country,",
            "  latest.platform,",
            "  latest.fulfillment_type AS fulfillmentType,",
            "  latest.sale_price AS salePrice,",
            "  latest.market_currency AS marketCurrency,",
            "  latest.effective_source_id AS effectiveSourceId,",
            "  latest.effective_source_type AS specSourceType,",
            "  latest.product_length_cm AS lengthCm,",
            "  latest.product_width_cm AS widthCm,",
            "  latest.product_height_cm AS heightCm,",
            "  latest.product_weight_g AS weightGrams,",
            "  latest.fee_amount AS feeAmount,",
            "  latest.currency,",
            "  latest.tax_multiplier AS taxMultiplier,",
            "  latest.tax_included_fee_amount AS taxIncludedFeeAmount,",
            "  latest.matched_classification_name AS matchedClassificationName,",
            "  latest.matched_slab_natural_key AS matchedSlabNaturalKey,",
            "  latest.source_version_id AS sourceVersionId,",
            "  latest.status,",
            "  latest.failure_code AS failureCode,",
            "  latest.message",
            "FROM (",
            "  SELECT DISTINCT",
            "    pv.id AS variantId,",
            "    CASE WHEN BINARY pv.partner_sku = BINARY requested.skuId THEN pv.partner_sku ELSE pv.child_sku END AS skuId",
            "  FROM (",
            "    <foreach collection='skuIds' item='skuId' separator=' UNION ALL '>",
            "      SELECT #{skuId} AS skuId",
            "    </foreach>",
            "  ) requested",
            "  JOIN product_variant pv",
            "    ON pv.is_deleted = b'0'",
            "   AND (BINARY pv.partner_sku = BINARY requested.skuId OR BINARY pv.child_sku = BINARY requested.skuId)",
            "  JOIN product_master pm",
            "    ON pm.id = pv.product_master_id",
            "   AND pm.is_deleted = b'0'",
            "  JOIN logical_store ls",
            "    ON ls.id = pm.logical_store_id",
            "   AND ls.owner_user_id = #{ownerUserId}",
            "   AND ls.is_deleted = b'0'",
            "  JOIN logical_store_site lss",
            "    ON lss.logical_store_id = ls.id",
            "   AND lss.store_code = #{storeCode}",
            "   AND lss.site = #{site}",
            "   AND lss.is_deleted = b'0'",
            ") sku_match",
            "JOIN official_outbound_fee_calculation_fact latest",
            "  ON latest.variant_id = sku_match.variantId",
            " AND latest.owner_user_id = #{ownerUserId}",
            " AND latest.store_code = #{storeCode}",
            " AND latest.site = #{site}",
            " <choose>",
            "   <when test='specSourceType != null and specSourceType != \"\"'>",
            "     AND latest.effective_source_type = #{specSourceType}",
            "   </when>",
            "   <otherwise>",
            "     AND COALESCE(latest.effective_source_type, '') &lt;&gt; 'noon_official'",
            "   </otherwise>",
            " </choose>",
            "JOIN (",
            "  SELECT variant_id, MAX(id) AS latestId",
            "  FROM official_outbound_fee_calculation_fact",
            "  WHERE owner_user_id = #{ownerUserId}",
            "    AND store_code = #{storeCode}",
            "    AND site = #{site}",
            "    <choose>",
            "      <when test='specSourceType != null and specSourceType != \"\"'>",
            "        AND effective_source_type = #{specSourceType}",
            "      </when>",
            "      <otherwise>",
            "        AND COALESCE(effective_source_type, '') &lt;&gt; 'noon_official'",
            "      </otherwise>",
            "    </choose>",
            "  GROUP BY variant_id",
            ") marker",
            "  ON marker.variant_id = latest.variant_id",
            " AND marker.latestId = latest.id",
            "ORDER BY sku_match.skuId ASC",
            "</script>"
    })
    List<OfficialOutboundFeeCalculationView> selectLatestCalculationViewsBySkuIds(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("site") String site,
            @Param("skuIds") List<String> skuIds,
            @Param("specSourceType") String specSourceType
    );
}
