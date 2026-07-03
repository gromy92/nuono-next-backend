package com.nuono.next.infrastructure.mapper;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ProductMatchRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostExceptionRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostHistoryRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;

public interface ProductLogisticsCostMapper {

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    default Long nextProductLogisticsCostHistoryId() {
        return nextId("product_logistics_cost_history", 370000L);
    }

    default Long nextProductLogisticsCurrentCostId() {
        return nextId("product_logistics_current_cost", 380000L);
    }

    default Long nextProductLogisticsRateCardId() {
        return nextId("product_logistics_rate_card", 430000L);
    }

    default Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("商品物流成本 ID 序列分配失败：" + sequenceName);
        }
        return command.getAllocatedId();
    }

    @Select({
            "SELECT ls.id",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "  AND (UPPER(lss.store_code) = UPPER(#{storeCode}) OR UPPER(ls.project_code) = UPPER(#{storeCode}))",
            "ORDER BY ls.id ASC",
            "LIMIT 1"
    })
    Long selectLogicalStoreIdByStoreCode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "<script>",
            "SELECT pm.logical_store_id AS logicalStoreId, pm.id AS productMasterId,",
            "       pv.id AS productVariantId, pv.partner_sku AS partnerSku,",
            "       pm.sku_parent AS skuParent,",
            "       (SELECT pb.barcode FROM product_barcode pb",
            "        WHERE pb.variant_id = pv.id AND pb.is_deleted = b'0'",
            "        ORDER BY pb.is_primary DESC, pb.id ASC LIMIT 1) AS barcode,",
            "       #{siteCode} AS siteCode",
            "FROM logical_store ls",
            "JOIN logical_store_site anchor ON anchor.logical_store_id = ls.id",
            " AND anchor.is_deleted = b'0'",
            " AND (UPPER(anchor.store_code) = UPPER(#{storeCode}) OR UPPER(ls.project_code) = UPPER(#{storeCode}))",
            "JOIN product_master pm ON pm.logical_store_id = ls.id AND pm.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "LEFT JOIN product_barcode matched_barcode",
            "  ON matched_barcode.variant_id = pv.id",
            " AND matched_barcode.is_deleted = b'0'",
            " AND BINARY matched_barcode.barcode = BINARY #{partnerSku}",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "  AND (BINARY pv.partner_sku = BINARY #{partnerSku} OR matched_barcode.id IS NOT NULL)",
            "<if test='siteCode != null and siteCode != \"\"'>",
            "  AND anchor.site = #{siteCode}",
            "</if>",
            "ORDER BY CASE",
            "  WHEN BINARY pv.partner_sku = BINARY #{partnerSku}",
            "   AND COALESCE(pm.title_cn_cache, pm.title_cache, '') NOT LIKE '待补资料商品 %' THEN 0",
            "  WHEN matched_barcode.id IS NOT NULL",
            "   AND COALESCE(pm.title_cn_cache, pm.title_cache, '') NOT LIKE '待补资料商品 %' THEN 1",
            "  WHEN BINARY pv.partner_sku = BINARY #{partnerSku} THEN 2",
            "  ELSE 3",
            "END, pv.id ASC",
            "LIMIT 2",
            "</script>"
    })
    List<ProductMatchRow> selectProductMatches(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("partnerSku") String partnerSku,
            @Param("siteCode") String siteCode
    );

    @Insert({
            "INSERT INTO product_logistics_cost_history (",
            "id, owner_user_id, logical_store_id, product_master_id, product_variant_id, partner_sku, barcode, site_code,",
            "forwarder_code, forwarder_name, transport_mode, route_code, route_name, service_code, service_name,",
            "in_transit_batch_id, batch_reference_no, source_type, cost_type, source_actual_bill_id, source_actual_component_id,",
            "source_shipping_order_id, source_quote_line_id, fee_type, raw_fee_name, cargo_category_code, cargo_category_name,",
            "quantity, charge_quantity, charge_unit, unit_cost, total_cost, currency_code, exchange_rate_to_cny,",
            "unit_cost_cny, total_cost_cny, allocation_basis, confidence_level, cost_occurred_at, idempotency_key,",
            "evidence_json, raw_snapshot_json, review_status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.productMasterId}, #{row.productVariantId}, #{row.partnerSku}, #{row.barcode}, #{row.siteCode},",
            "#{row.forwarderCode}, #{row.forwarderName}, #{row.transportMode}, #{row.routeCode}, #{row.routeName}, #{row.serviceCode}, #{row.serviceName},",
            "#{row.inTransitBatchId}, #{row.batchReferenceNo}, #{row.sourceType}, #{row.costType}, #{row.sourceActualBillId}, #{row.sourceActualComponentId},",
            "#{row.sourceShippingOrderId}, #{row.sourceQuoteLineId}, #{row.feeType}, #{row.rawFeeName}, #{row.cargoCategoryCode}, #{row.cargoCategoryName},",
            "#{row.quantity}, #{row.chargeQuantity}, #{row.chargeUnit}, #{row.unitCost}, #{row.totalCost}, #{row.currencyCode}, #{row.exchangeRateToCny},",
            "#{row.unitCostCny}, #{row.totalCostCny}, #{row.allocationBasis}, #{row.confidenceLevel}, #{row.costOccurredAt}, #{row.idempotencyKey},",
            "#{row.evidenceJson}, #{row.rawSnapshotJson}, #{row.reviewStatus}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertCostHistory(@Param("row") CostHistoryRow row, @Param("operatorUserId") Long operatorUserId);

    @Insert({
            "INSERT INTO product_logistics_current_cost (",
            "id, owner_user_id, logical_store_id, product_master_id, product_variant_id, partner_sku, barcode, site_code,",
            "forwarder_code, forwarder_name, transport_mode, route_code, route_name, service_code, service_name, current_history_id,",
            "source_type, cost_type, fee_type, cargo_category_code, cargo_category_name, charge_unit, unit_cost_cny, total_cost_cny,",
            "currency_code, confidence_level, cost_occurred_at, refreshed_at, evidence_json, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "LAST_INSERT_ID(#{row.id}), #{row.ownerUserId}, #{row.logicalStoreId}, #{row.productMasterId}, #{row.productVariantId}, #{row.partnerSku}, #{row.barcode}, #{row.siteCode},",
            "#{row.forwarderCode}, #{row.forwarderName}, #{row.transportMode}, #{row.routeCode}, #{row.routeName}, #{row.serviceCode}, #{row.serviceName}, #{row.currentHistoryId},",
            "#{row.sourceType}, #{row.costType}, #{row.feeType}, #{row.cargoCategoryCode}, #{row.cargoCategoryName}, #{row.chargeUnit}, #{row.unitCostCny}, #{row.totalCostCny},",
            "#{row.currencyCode}, #{row.confidenceLevel}, #{row.costOccurredAt}, NOW(), #{row.evidenceJson}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "id = LAST_INSERT_ID(id),",
            "product_master_id = VALUES(product_master_id),",
            "product_variant_id = VALUES(product_variant_id),",
            "barcode = VALUES(barcode),",
            "forwarder_name = VALUES(forwarder_name),",
            "route_code = VALUES(route_code),",
            "route_name = VALUES(route_name),",
            "service_code = VALUES(service_code),",
            "service_name = VALUES(service_name),",
            "current_history_id = VALUES(current_history_id),",
            "source_type = VALUES(source_type),",
            "cost_type = VALUES(cost_type),",
            "fee_type = VALUES(fee_type),",
            "cargo_category_code = VALUES(cargo_category_code),",
            "cargo_category_name = VALUES(cargo_category_name),",
            "charge_unit = VALUES(charge_unit),",
            "unit_cost_cny = VALUES(unit_cost_cny),",
            "total_cost_cny = VALUES(total_cost_cny),",
            "currency_code = VALUES(currency_code),",
            "confidence_level = VALUES(confidence_level),",
            "cost_occurred_at = VALUES(cost_occurred_at),",
            "refreshed_at = NOW(),",
            "evidence_json = VALUES(evidence_json),",
            "updated_by = #{operatorUserId},",
            "gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "row.id", before = false, resultType = Long.class)
    int upsertCurrentCost(@Param("row") CurrentCostRow row, @Param("operatorUserId") Long operatorUserId);

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, product_master_id AS productMasterId,",
            "product_variant_id AS productVariantId, partner_sku AS partnerSku, barcode, site_code AS siteCode,",
            "forwarder_code AS forwarderCode, forwarder_name AS forwarderName, transport_mode AS transportMode,",
            "route_code AS routeCode, route_name AS routeName, service_code AS serviceCode, service_name AS serviceName,",
            "current_history_id AS currentHistoryId, source_type AS sourceType, cost_type AS costType, fee_type AS feeType,",
            "cargo_category_code AS cargoCategoryCode, cargo_category_name AS cargoCategoryName,",
            "charge_unit AS chargeUnit, unit_cost_cny AS unitCostCny, total_cost_cny AS totalCostCny, currency_code AS currencyCode,",
            "confidence_level AS confidenceLevel, cost_occurred_at AS costOccurredAt, refreshed_at AS refreshedAt, evidence_json AS evidenceJson",
            "FROM product_logistics_current_cost",
            "WHERE owner_user_id = #{ownerUserId} AND is_deleted = b'0'",
            "<if test='logicalStoreId != null'> AND logical_store_id = #{logicalStoreId}</if>",
            "<if test='productVariantId != null'> AND product_variant_id = #{productVariantId}</if>",
            "<if test='partnerSku != null and partnerSku != \"\"'> AND partner_sku = #{partnerSku}</if>",
            "<if test='siteCode != null and siteCode != \"\"'> AND site_code = #{siteCode}</if>",
            "<if test='forwarderCode != null and forwarderCode != \"\"'> AND forwarder_code = #{forwarderCode}</if>",
            "<if test='transportMode != null and transportMode != \"\"'> AND transport_mode = #{transportMode}</if>",
            "ORDER BY refreshed_at DESC, id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<CurrentCostRow> listCurrentCosts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("productVariantId") Long productVariantId,
            @Param("partnerSku") String partnerSku,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, product_master_id AS productMasterId,",
            "product_variant_id AS productVariantId, partner_sku AS partnerSku, barcode, site_code AS siteCode,",
            "forwarder_code AS forwarderCode, forwarder_name AS forwarderName, transport_mode AS transportMode,",
            "route_code AS routeCode, route_name AS routeName, service_code AS serviceCode, service_name AS serviceName,",
            "current_history_id AS currentHistoryId, source_type AS sourceType, cost_type AS costType, fee_type AS feeType,",
            "cargo_category_code AS cargoCategoryCode, cargo_category_name AS cargoCategoryName,",
            "charge_unit AS chargeUnit, unit_cost_cny AS unitCostCny, total_cost_cny AS totalCostCny, currency_code AS currencyCode,",
            "confidence_level AS confidenceLevel, cost_occurred_at AS costOccurredAt, refreshed_at AS refreshedAt, evidence_json AS evidenceJson",
            "FROM product_logistics_current_cost",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND logical_store_id = #{logicalStoreId}",
            "  AND partner_sku = #{partnerSku}",
            "  AND site_code = #{siteCode}",
            "  AND forwarder_code = #{forwarderCode}",
            "  AND transport_mode = #{transportMode}",
            "  AND is_deleted = b'0'",
            "ORDER BY refreshed_at DESC, id DESC",
            "LIMIT 1"
    })
    CurrentCostRow selectCurrentCostForCategoryAssignment(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode
    );

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, product_master_id AS productMasterId,",
            "product_variant_id AS productVariantId, partner_sku AS partnerSku, barcode, site_code AS siteCode,",
            "forwarder_code AS forwarderCode, forwarder_name AS forwarderName, transport_mode AS transportMode,",
            "route_code AS routeCode, route_name AS routeName, service_code AS serviceCode, service_name AS serviceName,",
            "in_transit_batch_id AS inTransitBatchId, batch_reference_no AS batchReferenceNo, source_type AS sourceType, cost_type AS costType,",
            "source_actual_bill_id AS sourceActualBillId, source_actual_component_id AS sourceActualComponentId,",
            "source_shipping_order_id AS sourceShippingOrderId, source_quote_line_id AS sourceQuoteLineId, fee_type AS feeType,",
            "raw_fee_name AS rawFeeName, cargo_category_code AS cargoCategoryCode, cargo_category_name AS cargoCategoryName,",
            "quantity, charge_quantity AS chargeQuantity, charge_unit AS chargeUnit, unit_cost AS unitCost,",
            "total_cost AS totalCost, currency_code AS currencyCode, exchange_rate_to_cny AS exchangeRateToCny,",
            "unit_cost_cny AS unitCostCny, total_cost_cny AS totalCostCny, allocation_basis AS allocationBasis,",
            "confidence_level AS confidenceLevel, cost_occurred_at AS costOccurredAt, idempotency_key AS idempotencyKey,",
            "evidence_json AS evidenceJson, raw_snapshot_json AS rawSnapshotJson, review_status AS reviewStatus",
            "FROM product_logistics_cost_history",
            "WHERE owner_user_id = #{ownerUserId} AND is_deleted = b'0'",
            "<if test='logicalStoreId != null'> AND logical_store_id = #{logicalStoreId}</if>",
            "<if test='productVariantId != null'> AND product_variant_id = #{productVariantId}</if>",
            "<if test='partnerSku != null and partnerSku != \"\"'> AND partner_sku = #{partnerSku}</if>",
            "<if test='siteCode != null and siteCode != \"\"'> AND site_code = #{siteCode}</if>",
            "<if test='forwarderCode != null and forwarderCode != \"\"'> AND forwarder_code = #{forwarderCode}</if>",
            "<if test='transportMode != null and transportMode != \"\"'> AND transport_mode = #{transportMode}</if>",
            "ORDER BY cost_occurred_at DESC, id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<CostHistoryRow> listHistory(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("productVariantId") Long productVariantId,
            @Param("partnerSku") String partnerSku,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, product_master_id AS productMasterId,",
            "product_variant_id AS productVariantId, partner_sku AS partnerSku, barcode, site_code AS siteCode,",
            "forwarder_code AS forwarderCode, forwarder_name AS forwarderName, transport_mode AS transportMode,",
            "route_code AS routeCode, route_name AS routeName, service_code AS serviceCode, service_name AS serviceName,",
            "in_transit_batch_id AS inTransitBatchId, batch_reference_no AS batchReferenceNo, source_type AS sourceType, cost_type AS costType,",
            "source_actual_bill_id AS sourceActualBillId, source_actual_component_id AS sourceActualComponentId,",
            "source_shipping_order_id AS sourceShippingOrderId, source_quote_line_id AS sourceQuoteLineId, fee_type AS feeType,",
            "raw_fee_name AS rawFeeName, cargo_category_code AS cargoCategoryCode, cargo_category_name AS cargoCategoryName,",
            "quantity, charge_quantity AS chargeQuantity, charge_unit AS chargeUnit, unit_cost AS unitCost,",
            "total_cost AS totalCost, currency_code AS currencyCode, exchange_rate_to_cny AS exchangeRateToCny,",
            "unit_cost_cny AS unitCostCny, total_cost_cny AS totalCostCny, allocation_basis AS allocationBasis,",
            "confidence_level AS confidenceLevel, cost_occurred_at AS costOccurredAt, idempotency_key AS idempotencyKey,",
            "evidence_json AS evidenceJson, raw_snapshot_json AS rawSnapshotJson, review_status AS reviewStatus",
            "FROM product_logistics_cost_history",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND logical_store_id = #{logicalStoreId}",
            "  AND partner_sku = #{partnerSku}",
            "  AND site_code = #{siteCode}",
            "  AND forwarder_code = #{forwarderCode}",
            "  AND transport_mode = #{transportMode}",
            "  AND is_deleted = b'0'",
            "ORDER BY cost_occurred_at DESC, id DESC",
            "LIMIT 1"
    })
    CostHistoryRow selectLatestHistoryForCategoryAssignment(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSku") String partnerSku,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode
    );

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, site_code AS siteCode, forwarder_code AS forwarderCode,",
            "forwarder_name AS forwarderName, transport_mode AS transportMode, fee_type AS feeType,",
            "cargo_category_code AS cargoCategoryCode, cargo_category_name AS cargoCategoryName,",
            "charge_unit AS chargeUnit, unit_cost_cny AS unitCostCny, currency_code AS currencyCode,",
            "source_type AS sourceType, source_reference AS sourceReference, effective_at AS effectiveAt,",
            "remark, evidence_json AS evidenceJson",
            "FROM product_logistics_rate_card",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'",
            "  AND is_active = b'1'",
            "<if test='siteCode != null and siteCode != \"\"'> AND site_code = #{siteCode}</if>",
            "<if test='forwarderCode != null and forwarderCode != \"\"'> AND forwarder_code = #{forwarderCode}</if>",
            "<if test='transportMode != null and transportMode != \"\"'> AND transport_mode = #{transportMode}</if>",
            "ORDER BY site_code ASC, forwarder_code ASC, transport_mode ASC, cargo_category_code ASC, charge_unit ASC, id ASC",
            "</script>"
    })
    List<RateCardRow> listRateCards(
            @Param("ownerUserId") Long ownerUserId,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, site_code AS siteCode, forwarder_code AS forwarderCode,",
            "forwarder_name AS forwarderName, transport_mode AS transportMode, fee_type AS feeType,",
            "cargo_category_code AS cargoCategoryCode, cargo_category_name AS cargoCategoryName,",
            "charge_unit AS chargeUnit, unit_cost_cny AS unitCostCny, currency_code AS currencyCode,",
            "source_type AS sourceType, source_reference AS sourceReference, effective_at AS effectiveAt,",
            "remark, evidence_json AS evidenceJson",
            "FROM product_logistics_rate_card",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND site_code = #{siteCode}",
            "  AND forwarder_code = #{forwarderCode}",
            "  AND transport_mode = #{transportMode}",
            "  AND fee_type = #{feeType}",
            "  AND cargo_category_code = #{cargoCategoryCode}",
            "  AND is_deleted = b'0'",
            "  AND is_active = b'1'",
            "ORDER BY effective_at DESC, id DESC",
            "LIMIT 1"
    })
    RateCardRow selectRateCardForCategoryAssignment(
            @Param("ownerUserId") Long ownerUserId,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode,
            @Param("feeType") String feeType,
            @Param("cargoCategoryCode") String cargoCategoryCode
    );

    @Insert({
            "INSERT INTO product_logistics_rate_card (",
            "id, owner_user_id, site_code, forwarder_code, forwarder_name, transport_mode, fee_type,",
            "cargo_category_code, cargo_category_name, charge_unit, unit_cost_cny, currency_code,",
            "source_type, source_reference, effective_at, remark, evidence_json, is_active, is_deleted,",
            "created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "LAST_INSERT_ID(#{row.id}), #{row.ownerUserId}, #{row.siteCode}, #{row.forwarderCode}, #{row.forwarderName}, #{row.transportMode}, #{row.feeType},",
            "#{row.cargoCategoryCode}, #{row.cargoCategoryName}, #{row.chargeUnit}, #{row.unitCostCny}, #{row.currencyCode},",
            "#{row.sourceType}, #{row.sourceReference}, #{row.effectiveAt}, #{row.remark}, #{row.evidenceJson}, b'1', b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "id = LAST_INSERT_ID(id),",
            "forwarder_name = VALUES(forwarder_name),",
            "cargo_category_name = VALUES(cargo_category_name),",
            "unit_cost_cny = VALUES(unit_cost_cny),",
            "currency_code = VALUES(currency_code),",
            "source_type = VALUES(source_type),",
            "source_reference = VALUES(source_reference),",
            "effective_at = VALUES(effective_at),",
            "remark = VALUES(remark),",
            "evidence_json = VALUES(evidence_json),",
            "is_active = b'1',",
            "is_deleted = b'0',",
            "updated_by = #{operatorUserId},",
            "gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "row.id", before = false, resultType = Long.class)
    int upsertRateCard(@Param("row") RateCardRow row, @Param("operatorUserId") Long operatorUserId);

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, in_transit_batch_id AS inTransitBatchId, batch_reference_no AS batchReferenceNo,",
            "source_type AS sourceType, source_actual_bill_id AS sourceActualBillId, source_actual_component_id AS sourceActualComponentId,",
            "store_code AS storeCode, partner_sku AS partnerSku, site_code AS siteCode, forwarder_code AS forwarderCode, transport_mode AS transportMode,",
            "exception_type AS exceptionType, exception_message AS exceptionMessage, resolution_status AS resolutionStatus, evidence_json AS evidenceJson",
            "FROM product_logistics_cost_exception",
            "WHERE owner_user_id = #{ownerUserId} AND resolution_status = 'OPEN' AND is_deleted = b'0'",
            "ORDER BY gmt_create DESC, id DESC",
            "LIMIT #{limit}"
    })
    List<CostExceptionRow> listOpenExceptions(@Param("ownerUserId") Long ownerUserId, @Param("limit") Integer limit);
}
