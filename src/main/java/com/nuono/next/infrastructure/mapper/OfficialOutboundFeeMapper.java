package com.nuono.next.infrastructure.mapper;

import com.nuono.next.outboundfee.OutboundFeeCalculationPolicyFact;
import com.nuono.next.outboundfee.OutboundFeeWeightSlabRuleFact;
import com.nuono.next.outboundfee.OutboundSizeClassificationRuleFact;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface OfficialOutboundFeeMapper {

    @Select({
            "SELECT COALESCE(MAX(id), #{initialValue}) + 1",
            "FROM ${tableName}"
    })
    Long nextFactId(@Param("tableName") String tableName, @Param("initialValue") long initialValue);

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
            "  natural_key AS naturalKey, country, platform, fulfillment_type AS fulfillmentType, classification_name AS classificationName,",
            "  longest_side_max_cm AS longestSideMaxCm, median_side_max_cm AS medianSideMaxCm, shortest_side_max_cm AS shortestSideMaxCm,",
            "  max_shipping_weight_grams AS maxShippingWeightGrams, packaging_weight_grams AS packagingWeightGrams,",
            "  priority, dimension_unit AS dimensionUnit, weight_unit AS weightUnit, effective_from AS effectiveFrom, status,",
            "  source_type AS sourceType, source_task_id AS sourceTaskId, source_result_id AS sourceResultId,",
            "  source_version_id AS sourceVersionId, source_version_item_id AS sourceVersionItemId,",
            "  source_file_name AS sourceFileName, source_locator AS sourceLocator",
            "FROM official_outbound_size_classification_rule",
            "WHERE country = #{country}",
            "  AND platform = #{platform}",
            "  AND fulfillment_type = #{fulfillmentType}",
            "  AND status = 'ACTIVE'",
            "  AND (#{calculationDate} IS NULL OR effective_from IS NULL OR effective_from <= #{calculationDate})",
            "ORDER BY COALESCE(effective_from, DATE('1970-01-01')) DESC, priority ASC, id ASC"
    })
    List<Map<String, Object>> selectActiveSizeClassificationRows(
            @Param("country") String country,
            @Param("platform") String platform,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("calculationDate") LocalDate calculationDate
    );

    @Select({
            "SELECT",
            "  natural_key AS naturalKey, country, platform, fulfillment_type AS fulfillmentType, classification_name AS classificationName,",
            "  weight_min_grams AS weightMinGrams, weight_min_inclusive AS weightMinInclusive,",
            "  weight_max_grams AS weightMaxGrams, weight_max_inclusive AS weightMaxInclusive,",
            "  standard_fee_amount AS standardFeeAmount, high_asp_fee_amount AS highAspFeeAmount,",
            "  sales_price_threshold_amount AS salesPriceThresholdAmount, threshold_currency AS thresholdCurrency,",
            "  extra_weight_step_grams AS extraWeightStepGrams, extra_fee_amount AS extraFeeAmount, currency,",
            "  effective_from AS effectiveFrom, status,",
            "  source_type AS sourceType, source_task_id AS sourceTaskId, source_result_id AS sourceResultId,",
            "  source_version_id AS sourceVersionId, source_version_item_id AS sourceVersionItemId,",
            "  source_file_name AS sourceFileName, source_locator AS sourceLocator",
            "FROM official_outbound_fee_weight_slab_rule",
            "WHERE country = #{country}",
            "  AND platform = #{platform}",
            "  AND fulfillment_type = #{fulfillmentType}",
            "  AND status = 'ACTIVE'",
            "  AND (#{calculationDate} IS NULL OR effective_from IS NULL OR effective_from <= #{calculationDate})",
            "ORDER BY COALESCE(effective_from, DATE('1970-01-01')) DESC, weight_max_grams ASC, id ASC"
    })
    List<Map<String, Object>> selectActiveWeightSlabRows(
            @Param("country") String country,
            @Param("platform") String platform,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("calculationDate") LocalDate calculationDate
    );

    @Select({
            "SELECT",
            "  natural_key AS naturalKey, country, platform, fulfillment_type AS fulfillmentType, policy_name AS policyName,",
            "  shipping_weight_formula AS shippingWeightFormula, dimension_sort_rule AS dimensionSortRule,",
            "  weight_boundary_rule AS weightBoundaryRule, rounding_rule AS roundingRule,",
            "  sales_price_threshold_amount AS salesPriceThresholdAmount, threshold_currency AS thresholdCurrency,",
            "  dimension_unit AS dimensionUnit, weight_unit AS weightUnit, effective_from AS effectiveFrom, status,",
            "  source_type AS sourceType, source_task_id AS sourceTaskId, source_result_id AS sourceResultId,",
            "  source_version_id AS sourceVersionId, source_version_item_id AS sourceVersionItemId,",
            "  source_file_name AS sourceFileName, source_locator AS sourceLocator",
            "FROM official_outbound_fee_calculation_policy",
            "WHERE country = #{country}",
            "  AND platform = #{platform}",
            "  AND fulfillment_type = #{fulfillmentType}",
            "  AND status = 'ACTIVE'",
            "  AND (#{calculationDate} IS NULL OR effective_from IS NULL OR effective_from <= #{calculationDate})",
            "ORDER BY COALESCE(effective_from, DATE('1970-01-01')) DESC, id ASC",
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
}
