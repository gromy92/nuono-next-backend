package com.nuono.next.infrastructure.mapper;

import com.nuono.next.logisticsquote.LogisticsBillingRuleFact;
import com.nuono.next.logisticsquote.LogisticsCargoCategoryFact;
import com.nuono.next.logisticsquote.LogisticsPriceRuleFact;
import com.nuono.next.logisticsquote.LogisticsRestrictionRuleFact;
import com.nuono.next.logisticsquote.LogisticsServiceLineFact;
import com.nuono.next.logisticsquote.LogisticsSurchargeRuleFact;
import com.nuono.next.logisticsquote.LogisticsWarehouseFeeRuleFact;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface LogisticsQuoteFactMapper {

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

    @Insert({
            "INSERT INTO logistics_service_line (",
            "  id, natural_key, forwarder_code, forwarder_name, country, fulfillment_mode, destination_node,",
            "  transport_mode, service_scope, channel_name, origin_warehouse, destination_warehouse,",
            "  departure_frequency, estimated_days_min, estimated_days_max, effective_from,",
            "  source_type, source_task_id, source_result_id, source_version_id, source_version_item_id,",
            "  source_file_name, source_locator, status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.naturalKey}, #{fact.forwarderCode}, #{fact.forwarderName}, #{fact.country},",
            "  #{fact.fulfillmentMode}, #{fact.destinationNode}, #{fact.transportMode}, #{fact.serviceScope},",
            "  #{fact.channelName}, #{fact.originWarehouse}, #{fact.destinationWarehouse}, #{fact.departureFrequency},",
            "  #{fact.estimatedDaysMin}, #{fact.estimatedDaysMax}, #{fact.effectiveFrom},",
            "  #{fact.sourceLineage.sourceType}, #{fact.sourceLineage.sourceTaskId}, #{fact.sourceLineage.sourceResultId},",
            "  #{fact.sourceLineage.sourceVersionId}, #{fact.sourceLineage.sourceVersionItemId},",
            "  #{fact.sourceLineage.sourceFileName}, #{fact.sourceLineage.sourceLocator}, #{fact.status},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertServiceLine(
            @Param("id") Long id,
            @Param("fact") LogisticsServiceLineFact fact,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO logistics_cargo_category (",
            "  id, natural_key, forwarder_code, service_line_key, category_code, category_name, source_category_name,",
            "  product_examples, keywords, electric_type, sensitive_tags, packing_policy, manual_confirm_required,",
            "  source_type, source_task_id, source_result_id, source_version_id, source_version_item_id,",
            "  source_file_name, source_locator, status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.naturalKey}, #{fact.forwarderCode}, #{fact.serviceLineKey}, #{fact.categoryCode},",
            "  #{fact.categoryName}, #{fact.sourceCategoryName}, #{fact.productExamples}, #{fact.keywords},",
            "  #{fact.electricType}, #{fact.sensitiveTags}, #{fact.packingPolicy}, #{fact.manualConfirmRequired},",
            "  #{fact.sourceLineage.sourceType}, #{fact.sourceLineage.sourceTaskId}, #{fact.sourceLineage.sourceResultId},",
            "  #{fact.sourceLineage.sourceVersionId}, #{fact.sourceLineage.sourceVersionItemId},",
            "  #{fact.sourceLineage.sourceFileName}, #{fact.sourceLineage.sourceLocator}, #{fact.status},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertCargoCategory(
            @Param("id") Long id,
            @Param("fact") LogisticsCargoCategoryFact fact,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO logistics_price_rule (",
            "  id, natural_key, forwarder_code, service_line_key, cargo_category_key, unit_price, currency,",
            "  billing_unit, pricing_model, minimum_billable_unit, minimum_billable_unit_type, minimum_charge,",
            "  volume_divisor, sea_weight_ratio, rounding_rule, price_status, effective_from,",
            "  source_type, source_task_id, source_result_id, source_version_id, source_version_item_id,",
            "  source_file_name, source_locator, status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.naturalKey}, #{fact.forwarderCode}, #{fact.serviceLineKey}, #{fact.cargoCategoryKey},",
            "  #{fact.unitPrice}, #{fact.currency}, #{fact.billingUnit}, #{fact.pricingModel},",
            "  #{fact.minimumBillableUnit}, #{fact.minimumBillableUnitType}, #{fact.minimumCharge},",
            "  #{fact.volumeDivisor}, #{fact.seaWeightRatio}, #{fact.roundingRule}, #{fact.priceStatus}, #{fact.effectiveFrom},",
            "  #{fact.sourceLineage.sourceType}, #{fact.sourceLineage.sourceTaskId}, #{fact.sourceLineage.sourceResultId},",
            "  #{fact.sourceLineage.sourceVersionId}, #{fact.sourceLineage.sourceVersionItemId},",
            "  #{fact.sourceLineage.sourceFileName}, #{fact.sourceLineage.sourceLocator}, #{fact.status},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertPriceRule(
            @Param("id") Long id,
            @Param("fact") LogisticsPriceRuleFact fact,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO logistics_surcharge_rule (",
            "  id, natural_key, forwarder_code, service_line_key, surcharge_name, surcharge_type, trigger_condition,",
            "  pricing_model, amount, rate, currency, billing_unit, minimum_charge, included_in_base_price,",
            "  source_type, source_task_id, source_result_id, source_version_id, source_version_item_id,",
            "  source_file_name, source_locator, status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.naturalKey}, #{fact.forwarderCode}, #{fact.serviceLineKey}, #{fact.surchargeName},",
            "  #{fact.surchargeType}, #{fact.triggerCondition}, #{fact.pricingModel}, #{fact.amount}, #{fact.rate},",
            "  #{fact.currency}, #{fact.billingUnit}, #{fact.minimumCharge}, #{fact.includedInBasePrice},",
            "  #{fact.sourceLineage.sourceType}, #{fact.sourceLineage.sourceTaskId}, #{fact.sourceLineage.sourceResultId},",
            "  #{fact.sourceLineage.sourceVersionId}, #{fact.sourceLineage.sourceVersionItemId},",
            "  #{fact.sourceLineage.sourceFileName}, #{fact.sourceLineage.sourceLocator}, #{fact.status},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertSurchargeRule(
            @Param("id") Long id,
            @Param("fact") LogisticsSurchargeRuleFact fact,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO logistics_billing_rule (",
            "  id, natural_key, forwarder_code, service_line_key, cargo_category_key, rule_name, rule_type,",
            "  condition_text, structured_field, operator, threshold_value, threshold_unit, action_text, severity,",
            "  source_type, source_task_id, source_result_id, source_version_id, source_version_item_id,",
            "  source_file_name, source_locator, status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.naturalKey}, #{fact.forwarderCode}, #{fact.serviceLineKey}, #{fact.cargoCategoryKey},",
            "  #{fact.ruleName}, #{fact.ruleType}, #{fact.conditionText}, #{fact.structuredField}, #{fact.operator},",
            "  #{fact.thresholdValue}, #{fact.thresholdUnit}, #{fact.actionText}, #{fact.severity},",
            "  #{fact.sourceLineage.sourceType}, #{fact.sourceLineage.sourceTaskId}, #{fact.sourceLineage.sourceResultId},",
            "  #{fact.sourceLineage.sourceVersionId}, #{fact.sourceLineage.sourceVersionItemId},",
            "  #{fact.sourceLineage.sourceFileName}, #{fact.sourceLineage.sourceLocator}, #{fact.status},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertBillingRule(
            @Param("id") Long id,
            @Param("fact") LogisticsBillingRuleFact fact,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO logistics_restriction_rule (",
            "  id, natural_key, forwarder_code, service_line_key, restriction_type, item_text, requirement_text,",
            "  applicability_scope, severity, manual_confirm_required,",
            "  source_type, source_task_id, source_result_id, source_version_id, source_version_item_id,",
            "  source_file_name, source_locator, status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.naturalKey}, #{fact.forwarderCode}, #{fact.serviceLineKey}, #{fact.restrictionType},",
            "  #{fact.itemText}, #{fact.requirementText}, #{fact.applicabilityScope}, #{fact.severity},",
            "  #{fact.manualConfirmRequired},",
            "  #{fact.sourceLineage.sourceType}, #{fact.sourceLineage.sourceTaskId}, #{fact.sourceLineage.sourceResultId},",
            "  #{fact.sourceLineage.sourceVersionId}, #{fact.sourceLineage.sourceVersionItemId},",
            "  #{fact.sourceLineage.sourceFileName}, #{fact.sourceLineage.sourceLocator}, #{fact.status},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertRestrictionRule(
            @Param("id") Long id,
            @Param("fact") LogisticsRestrictionRuleFact fact,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO logistics_warehouse_fee_rule (",
            "  id, natural_key, forwarder_code, country, warehouse_node, service_name, service_type, processing_scope,",
            "  fee_type, pricing_model, amount, rate, currency, billing_unit, condition_text, free_condition,",
            "  source_type, source_task_id, source_result_id, source_version_id, source_version_item_id,",
            "  source_file_name, source_locator, status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.naturalKey}, #{fact.forwarderCode}, #{fact.country}, #{fact.warehouseNode},",
            "  #{fact.serviceName}, #{fact.serviceType}, #{fact.processingScope}, #{fact.feeType}, #{fact.pricingModel},",
            "  #{fact.amount}, #{fact.rate}, #{fact.currency}, #{fact.billingUnit}, #{fact.conditionText}, #{fact.freeCondition},",
            "  #{fact.sourceLineage.sourceType}, #{fact.sourceLineage.sourceTaskId}, #{fact.sourceLineage.sourceResultId},",
            "  #{fact.sourceLineage.sourceVersionId}, #{fact.sourceLineage.sourceVersionItemId},",
            "  #{fact.sourceLineage.sourceFileName}, #{fact.sourceLineage.sourceLocator}, #{fact.status},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertWarehouseFeeRule(
            @Param("id") Long id,
            @Param("fact") LogisticsWarehouseFeeRuleFact fact,
            @Param("operatorUserId") Long operatorUserId
    );
}
