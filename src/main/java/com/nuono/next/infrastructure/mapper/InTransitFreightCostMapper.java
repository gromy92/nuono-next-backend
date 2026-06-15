package com.nuono.next.infrastructure.mapper;

import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightBillRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightComponentRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateComponentRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateMatchRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateSnapshotRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.ForwarderFreightComparisonRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.FreightStatisticsRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardRuleRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardVersionRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.SkuFreightCostHistoryRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface InTransitFreightCostMapper {

    String ACTUAL_BILL_SELECT = ""
            + "SELECT id, owner_user_id, batch_id, standard_forwarder_id, forwarder_code, forwarder_name, "
            + "transport_mode, destination_code, target_site_code, source_type, source_system, bill_no, bill_status, business_occurred_at, "
            + "bill_date, paid_at, currency_code, exchange_rate_to_cny, original_total_amount, cny_total_amount, "
            + "freight_amount_cny, customs_amount_cny, storage_amount_cny, handling_amount_cny, delivery_amount_cny, "
            + "interest_amount_cny, posted_amount_cny, balance_amount_cny, raw_payload_json, created_by, updated_by "
            + "FROM in_transit_freight_actual_bill ";

    String ACTUAL_COMPONENT_SELECT = ""
            + "SELECT id, owner_user_id, actual_bill_id, batch_id, package_id, box_no, external_box_no, psku, "
            + "transport_mode, destination_code, target_site_code, raw_fee_name, standard_fee_type, "
            + "charge_quantity, charge_unit, unit_price, currency_code, "
            + "exchange_rate_to_cny, original_amount, cny_amount, quantity, measured_weight_kg, measured_volume_cbm, "
            + "volume_weight_kg, chargeable_weight_kg, allocation_basis, raw_payload_json, created_by, updated_by "
            + "FROM in_transit_freight_actual_component ";

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    default Long nextActualBillId() {
        return nextId("in_transit_freight_actual_bill", 59000L);
    }

    default Long nextActualComponentId() {
        return nextId("in_transit_freight_actual_component", 60000L);
    }

    default Long nextEstimateSnapshotId() {
        return nextId("in_transit_freight_estimate_snapshot", 61000L);
    }

    default Long nextEstimateComponentId() {
        return nextId("in_transit_freight_estimate_component", 62000L);
    }

    default Long nextEstimateMatchId() {
        return nextId("in_transit_freight_estimate_match", 63000L);
    }

    default Long nextRateCardVersionId() {
        return nextId("in_transit_freight_rate_card_version", 64000L);
    }

    default Long nextRateCardRuleId() {
        return nextId("in_transit_freight_rate_card_rule", 65000L);
    }

    default Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("在途运费 ID 序列分配失败：" + sequenceName);
        }
        return command.getAllocatedId();
    }

    @Select({
            "SELECT id, owner_user_id, batch_id, box_no, external_box_no, tracking_no,",
            "weight_kg, length_cm, width_cm, height_cm, volume_cbm, volume_weight_kg, chargeable_weight_kg,",
            "measured_weight_kg, measured_length_cm, measured_width_cm, measured_height_cm, measured_volume_cbm,",
            "package_status, logistics_status, created_by, updated_by",
            "FROM in_transit_package",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'",
            "AND (",
            "(#{boxNo} IS NOT NULL AND #{boxNo} != '' AND box_no = #{boxNo})",
            "OR (#{externalBoxNo} IS NOT NULL AND #{externalBoxNo} != '' AND external_box_no = #{externalBoxNo})",
            ")",
            "ORDER BY CASE WHEN box_no = #{boxNo} THEN 0 ELSE 1 END, id ASC",
            "LIMIT 1"
    })
    PackageRow selectPackageByAnyBoxNo(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("boxNo") String boxNo,
            @Param("externalBoxNo") String externalBoxNo
    );

    @Select({
            ACTUAL_BILL_SELECT,
            "WHERE owner_user_id = #{ownerUserId} AND source_system = #{sourceSystem}",
            "AND bill_no = #{billNo} AND batch_id = #{batchId} AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ActualFreightBillRow selectActualBillBySource(
            @Param("ownerUserId") Long ownerUserId,
            @Param("sourceSystem") String sourceSystem,
            @Param("billNo") String billNo,
            @Param("batchId") Long batchId
    );

    @Insert({
            "INSERT INTO in_transit_freight_actual_bill (",
            "id, owner_user_id, batch_id, standard_forwarder_id, forwarder_code, forwarder_name,",
            "transport_mode, destination_code, target_site_code, source_type, source_system,",
            "bill_no, bill_status, business_occurred_at, bill_date, paid_at, currency_code, exchange_rate_to_cny,",
            "original_total_amount, cny_total_amount, freight_amount_cny, customs_amount_cny, storage_amount_cny, handling_amount_cny,",
            "delivery_amount_cny, interest_amount_cny, posted_amount_cny, balance_amount_cny, raw_payload_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.batchId}, #{row.standardForwarderId}, #{row.forwarderCode}, #{row.forwarderName},",
            "#{row.transportMode}, #{row.destinationCode}, #{row.targetSiteCode},",
            "#{row.sourceType}, #{row.sourceSystem}, #{row.billNo}, #{row.billStatus},",
            "#{row.businessOccurredAt}, #{row.billDate}, #{row.paidAt}, #{row.currencyCode}, #{row.exchangeRateToCny},",
            "#{row.originalTotalAmount}, #{row.cnyTotalAmount}, #{row.freightAmountCny}, #{row.customsAmountCny},",
            "#{row.storageAmountCny}, #{row.handlingAmountCny}, #{row.deliveryAmountCny}, #{row.interestAmountCny},",
            "#{row.postedAmountCny}, #{row.balanceAmountCny}, #{row.rawPayloadJson}, b'0',",
            "#{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertActualBill(@Param("row") ActualFreightBillRow row);

    @Update({
            "UPDATE in_transit_freight_actual_bill",
            "SET standard_forwarder_id = #{row.standardForwarderId}, forwarder_code = #{row.forwarderCode}, forwarder_name = #{row.forwarderName},",
            "transport_mode = #{row.transportMode}, destination_code = #{row.destinationCode}, target_site_code = #{row.targetSiteCode},",
            "bill_status = #{row.billStatus}, business_occurred_at = #{row.businessOccurredAt},",
            "bill_date = #{row.billDate}, paid_at = #{row.paidAt}, currency_code = #{row.currencyCode},",
            "exchange_rate_to_cny = #{row.exchangeRateToCny}, original_total_amount = #{row.originalTotalAmount},",
            "cny_total_amount = #{row.cnyTotalAmount}, freight_amount_cny = #{row.freightAmountCny},",
            "customs_amount_cny = #{row.customsAmountCny}, storage_amount_cny = #{row.storageAmountCny},",
            "handling_amount_cny = #{row.handlingAmountCny}, delivery_amount_cny = #{row.deliveryAmountCny},",
            "interest_amount_cny = #{row.interestAmountCny}, posted_amount_cny = #{row.postedAmountCny},",
            "balance_amount_cny = #{row.balanceAmountCny}, raw_payload_json = #{row.rawPayloadJson},",
            "updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updateActualBill(@Param("row") ActualFreightBillRow row);

    @Update({
            "UPDATE in_transit_freight_actual_component",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND actual_bill_id = #{actualBillId} AND is_deleted = b'0'"
    })
    int softDeleteActualComponents(
            @Param("ownerUserId") Long ownerUserId,
            @Param("actualBillId") Long actualBillId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO in_transit_freight_actual_component (",
            "id, owner_user_id, actual_bill_id, batch_id, package_id, box_no, external_box_no, psku,",
            "transport_mode, destination_code, target_site_code, raw_fee_name, standard_fee_type,",
            "charge_quantity, charge_unit, unit_price, currency_code, exchange_rate_to_cny, original_amount, cny_amount, quantity,",
            "measured_weight_kg, measured_volume_cbm, volume_weight_kg, chargeable_weight_kg, allocation_basis, raw_payload_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.actualBillId}, #{row.batchId}, #{row.packageId}, #{row.boxNo},",
            "#{row.externalBoxNo}, #{row.psku}, #{row.transportMode}, #{row.destinationCode}, #{row.targetSiteCode},",
            "#{row.rawFeeName}, #{row.standardFeeType}, #{row.chargeQuantity},",
            "#{row.chargeUnit}, #{row.unitPrice}, #{row.currencyCode}, #{row.exchangeRateToCny}, #{row.originalAmount},",
            "#{row.cnyAmount}, #{row.quantity}, #{row.measuredWeightKg}, #{row.measuredVolumeCbm}, #{row.volumeWeightKg},",
            "#{row.chargeableWeightKg}, #{row.allocationBasis}, #{row.rawPayloadJson}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertActualComponent(@Param("row") ActualFreightComponentRow row);

    @Select({
            ACTUAL_BILL_SELECT,
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'",
            "ORDER BY COALESCE(business_occurred_at, bill_date, gmt_create) DESC, id DESC"
    })
    List<ActualFreightBillRow> listActualBillsByBatch(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId
    );

    @Select({
            ACTUAL_COMPONENT_SELECT,
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'",
            "ORDER BY actual_bill_id ASC, box_no ASC, external_box_no ASC, id ASC"
    })
    List<ActualFreightComponentRow> listActualComponentsByBatch(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId
    );

    @Select({
            "<script>",
            "SELECT DATE_FORMAT(COALESCE(bill.business_occurred_at, bill.bill_date, bill.gmt_create), '%Y-%m') AS month,",
            "bill.standard_forwarder_id, bill.transport_mode, bill.destination_code, bill.target_site_code,",
            "bill.forwarder_code, bill.forwarder_name,",
            "COUNT(DISTINCT bill.batch_id) AS batch_count,",
            "COUNT(DISTINCT bill.id) AS bill_count,",
            "SUM(COALESCE(component_summary.component_count, 0)) AS component_count,",
            "SUM(COALESCE(bill.cny_total_amount, 0)) AS total_amount_cny,",
            "SUM(COALESCE(bill.freight_amount_cny, 0)) AS freight_amount_cny,",
            "SUM(COALESCE(bill.customs_amount_cny, 0)) AS customs_amount_cny,",
            "SUM(COALESCE(component_summary.chargeable_weight_kg, 0)) AS chargeable_weight_kg",
            "FROM in_transit_freight_actual_bill bill",
            "LEFT JOIN (",
            "SELECT owner_user_id, actual_bill_id, COUNT(*) AS component_count,",
            "SUM(COALESCE(chargeable_weight_kg, 0)) AS chargeable_weight_kg",
            "FROM in_transit_freight_actual_component",
            "WHERE is_deleted = b'0'",
            "GROUP BY owner_user_id, actual_bill_id",
            ") component_summary",
            "ON component_summary.owner_user_id = bill.owner_user_id AND component_summary.actual_bill_id = bill.id",
            "WHERE bill.owner_user_id = #{ownerUserId} AND bill.is_deleted = b'0'",
            "<if test='fromInclusive != null'>",
            "AND COALESCE(bill.business_occurred_at, bill.bill_date, bill.gmt_create) &gt;= #{fromInclusive}",
            "</if>",
            "<if test='toExclusive != null'>",
            "AND COALESCE(bill.business_occurred_at, bill.bill_date, bill.gmt_create) &lt; #{toExclusive}",
            "</if>",
            "<if test='standardForwarderId != null'> AND bill.standard_forwarder_id = #{standardForwarderId}</if>",
            "GROUP BY month, bill.standard_forwarder_id, bill.transport_mode, bill.destination_code, bill.target_site_code, bill.forwarder_code, bill.forwarder_name",
            "ORDER BY month DESC, bill.forwarder_name ASC",
            "</script>"
    })
    List<FreightStatisticsRow> statistics(
            @Param("ownerUserId") Long ownerUserId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("standardForwarderId") Long standardForwarderId
    );

    @Select({
            "<script>",
            "SELECT component.psku, component.target_site_code, component.transport_mode, component.destination_code,",
            "bill.standard_forwarder_id, bill.forwarder_name, component.standard_fee_type,",
            "SUM(COALESCE(component.quantity, 0)) AS quantity,",
            "SUM(COALESCE(component.charge_quantity, 0)) AS charge_quantity,",
            "component.charge_unit,",
            "SUM(COALESCE(component.cny_amount, 0)) AS total_amount_cny,",
            "CASE WHEN SUM(COALESCE(component.quantity, 0)) = 0 THEN NULL",
            "ELSE SUM(COALESCE(component.cny_amount, 0)) / SUM(COALESCE(component.quantity, 0)) END AS unit_amount_cny,",
            "MAX(COALESCE(bill.business_occurred_at, bill.bill_date, bill.gmt_create)) AS business_occurred_at",
            "FROM in_transit_freight_actual_component component",
            "JOIN in_transit_freight_actual_bill bill",
            "ON bill.owner_user_id = component.owner_user_id",
            "AND bill.id = component.actual_bill_id",
            "AND bill.is_deleted = b'0'",
            "WHERE component.owner_user_id = #{ownerUserId}",
            "AND component.psku = #{psku}",
            "AND component.target_site_code = #{targetSiteCode}",
            "AND component.is_deleted = b'0'",
            "<if test='fromInclusive != null'>",
            "AND COALESCE(bill.business_occurred_at, bill.bill_date, bill.gmt_create) &gt;= #{fromInclusive}",
            "</if>",
            "<if test='toExclusive != null'>",
            "AND COALESCE(bill.business_occurred_at, bill.bill_date, bill.gmt_create) &lt; #{toExclusive}",
            "</if>",
            "GROUP BY component.psku, component.target_site_code, component.transport_mode,",
            "component.destination_code, bill.standard_forwarder_id, bill.forwarder_name,",
            "component.standard_fee_type, component.charge_unit",
            "ORDER BY business_occurred_at DESC",
            "</script>"
    })
    List<SkuFreightCostHistoryRow> listSkuSiteActualCosts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("psku") String psku,
            @Param("targetSiteCode") String targetSiteCode,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    @Select({
            "<script>",
            "SELECT bill.standard_forwarder_id, bill.forwarder_code, bill.forwarder_name,",
            "component.transport_mode, component.destination_code, component.target_site_code,",
            "component.standard_fee_type, component.charge_unit,",
            "SUM(COALESCE(component.cny_amount, 0)) AS total_amount_cny,",
            "SUM(COALESCE(component.charge_quantity, 0)) AS total_charge_quantity,",
            "SUM(COALESCE(component.quantity, 0)) AS total_quantity,",
            "CASE WHEN SUM(COALESCE(component.charge_quantity, 0)) = 0 THEN NULL",
            "ELSE SUM(COALESCE(component.cny_amount, 0)) / SUM(COALESCE(component.charge_quantity, 0)) END AS amount_per_unit,",
            "COUNT(DISTINCT bill.batch_id) AS shipment_count",
            "FROM in_transit_freight_actual_component component",
            "JOIN in_transit_freight_actual_bill bill",
            "ON bill.owner_user_id = component.owner_user_id",
            "AND bill.id = component.actual_bill_id",
            "AND bill.is_deleted = b'0'",
            "WHERE component.owner_user_id = #{ownerUserId}",
            "AND component.psku = #{psku}",
            "AND component.target_site_code = #{targetSiteCode}",
            "AND component.is_deleted = b'0'",
            "<if test='transportMode != null and transportMode != \"\"'>",
            "AND component.transport_mode = #{transportMode}",
            "</if>",
            "<if test='destinationCode != null and destinationCode != \"\"'>",
            "AND component.destination_code = #{destinationCode}",
            "</if>",
            "GROUP BY bill.standard_forwarder_id, bill.forwarder_code, bill.forwarder_name,",
            "component.transport_mode, component.destination_code, component.target_site_code,",
            "component.standard_fee_type, component.charge_unit",
            "ORDER BY amount_per_unit ASC, total_amount_cny ASC",
            "</script>"
    })
    List<ForwarderFreightComparisonRow> compareForwardersForSkuSite(
            @Param("ownerUserId") Long ownerUserId,
            @Param("psku") String psku,
            @Param("targetSiteCode") String targetSiteCode,
            @Param("transportMode") String transportMode,
            @Param("destinationCode") String destinationCode
    );

    @Insert({
            "INSERT INTO in_transit_freight_rate_card_version (",
            "id, owner_user_id, standard_forwarder_id, forwarder_code, forwarder_name, version_no, version_name,",
            "transport_mode, destination_code, target_site_code, currency_code, exchange_rate_to_cny,",
            "effective_from, effective_to, version_status, source_type, raw_payload_json,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.standardForwarderId}, #{row.forwarderCode}, #{row.forwarderName},",
            "#{row.versionNo}, #{row.versionName}, #{row.transportMode}, #{row.destinationCode}, #{row.targetSiteCode},",
            "#{row.currencyCode}, #{row.exchangeRateToCny}, #{row.effectiveFrom}, #{row.effectiveTo},",
            "#{row.versionStatus}, #{row.sourceType}, #{row.rawPayloadJson}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertRateCardVersion(@Param("row") RateCardVersionRow row);

    @Insert({
            "INSERT INTO in_transit_freight_rate_card_rule (",
            "id, owner_user_id, rate_card_version_id, standard_fee_type, raw_fee_name, product_category, box_category,",
            "charge_unit, unit_price, min_charge_quantity, min_amount_cny, currency_code, exchange_rate_to_cny,",
            "rule_status, formula_json, raw_payload_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.rateCardVersionId}, #{row.standardFeeType}, #{row.rawFeeName},",
            "#{row.productCategory}, #{row.boxCategory}, #{row.chargeUnit}, #{row.unitPrice}, #{row.minChargeQuantity},",
            "#{row.minAmountCny}, #{row.currencyCode}, #{row.exchangeRateToCny}, #{row.ruleStatus},",
            "#{row.formulaJson}, #{row.rawPayloadJson}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertRateCardRule(@Param("row") RateCardRuleRow row);

    @Update({
            "UPDATE in_transit_freight_estimate_snapshot",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "AND source_estimate_type = #{sourceEstimateType}",
            "AND (",
            "(#{sourceEstimateId} IS NULL AND source_estimate_id IS NULL)",
            "OR source_estimate_id = #{sourceEstimateId}",
            ")",
            "AND (",
            "(#{sourceRecommendationId} IS NULL AND source_recommendation_id IS NULL)",
            "OR source_recommendation_id = #{sourceRecommendationId}",
            ")",
            "AND is_deleted = b'0'"
    })
    int softDeleteEstimateSnapshotBySource(
            @Param("ownerUserId") Long ownerUserId,
            @Param("sourceEstimateType") String sourceEstimateType,
            @Param("sourceEstimateId") Long sourceEstimateId,
            @Param("sourceRecommendationId") Long sourceRecommendationId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO in_transit_freight_estimate_snapshot (",
            "id, owner_user_id, batch_id, source_estimate_type, source_estimate_id, source_estimate_no, source_recommendation_id,",
            "rate_card_version_id, standard_forwarder_id, forwarder_code, forwarder_name, transport_mode, destination_code,",
            "target_site_code, recommended, estimate_status, currency_code, exchange_rate_to_cny, estimated_total_amount,",
            "estimated_total_cny, generated_at, raw_payload_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.batchId}, #{row.sourceEstimateType}, #{row.sourceEstimateId},",
            "#{row.sourceEstimateNo}, #{row.sourceRecommendationId}, #{row.rateCardVersionId}, #{row.standardForwarderId},",
            "#{row.forwarderCode}, #{row.forwarderName}, #{row.transportMode}, #{row.destinationCode}, #{row.targetSiteCode},",
            "#{row.recommended}, #{row.estimateStatus}, #{row.currencyCode}, #{row.exchangeRateToCny},",
            "#{row.estimatedTotalAmount}, #{row.estimatedTotalCny}, #{row.generatedAt}, #{row.rawPayloadJson}, b'0',",
            "#{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertEstimateSnapshot(@Param("row") EstimateSnapshotRow row);

    @Insert({
            "INSERT INTO in_transit_freight_estimate_component (",
            "id, owner_user_id, estimate_snapshot_id, target_site_code, psku, component_type, raw_fee_name, quantity,",
            "chargeable_weight_kg, charge_quantity, charge_unit, unit_price, currency_code, exchange_rate_to_cny,",
            "estimated_amount, estimated_amount_cny, raw_payload_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.estimateSnapshotId}, #{row.targetSiteCode}, #{row.psku},",
            "#{row.componentType}, #{row.rawFeeName}, #{row.quantity}, #{row.chargeableWeightKg}, #{row.chargeQuantity},",
            "#{row.chargeUnit}, #{row.unitPrice}, #{row.currencyCode}, #{row.exchangeRateToCny}, #{row.estimatedAmount},",
            "#{row.estimatedAmountCny}, #{row.rawPayloadJson}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertEstimateComponent(@Param("row") EstimateComponentRow row);

    @Select({
            "SELECT SUM(COALESCE(cny_total_amount, 0))",
            "FROM in_transit_freight_actual_bill",
            "WHERE owner_user_id = #{ownerUserId}",
            "AND batch_id = #{batchId}",
            "AND is_deleted = b'0'"
    })
    BigDecimal sumActualBillByBatch(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId
    );

    @Select({
            "SELECT id, owner_user_id, batch_id, source_estimate_type, source_estimate_id, source_estimate_no,",
            "source_recommendation_id, rate_card_version_id, standard_forwarder_id, forwarder_code, forwarder_name,",
            "transport_mode, destination_code, target_site_code, recommended, estimate_status, currency_code,",
            "exchange_rate_to_cny, estimated_total_amount, estimated_total_cny, generated_at, raw_payload_json,",
            "created_by, updated_by",
            "FROM in_transit_freight_estimate_snapshot",
            "WHERE owner_user_id = #{ownerUserId}",
            "AND batch_id = #{batchId}",
            "AND recommended = b'1'",
            "AND is_deleted = b'0'",
            "ORDER BY COALESCE(generated_at, gmt_create) DESC, id DESC"
    })
    List<EstimateSnapshotRow> listRecommendedEstimatesByBatch(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId
    );

    @Update({
            "UPDATE in_transit_freight_estimate_match",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "AND batch_id = #{batchId}",
            "AND is_deleted = b'0'"
    })
    int softDeleteEstimateMatchesByBatch(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO in_transit_freight_estimate_match (",
            "id, owner_user_id, batch_id, actual_bill_id, estimate_snapshot_id, match_status, actual_total_cny,",
            "estimated_total_cny, diff_amount_cny, diff_rate, matched_at, reason, is_deleted, created_by, updated_by,",
            "gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.batchId}, #{row.actualBillId}, #{row.estimateSnapshotId},",
            "#{row.matchStatus}, #{row.actualTotalCny}, #{row.estimatedTotalCny}, #{row.diffAmountCny},",
            "#{row.diffRate}, #{row.matchedAt}, #{row.reason}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertEstimateMatch(@Param("row") EstimateMatchRow row);
}
