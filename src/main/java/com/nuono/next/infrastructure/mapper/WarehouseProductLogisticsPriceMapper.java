package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WarehouseProductLogisticsPriceMapper {

    @Select({
            "SELECT ls.is_deleted = b'1'",
            "FROM logical_store ls",
            "WHERE ls.id = #{logicalStoreId}",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "LIMIT 1"
    })
    Boolean selectLogicalStoreArchived(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId
    );

    @Select({
            "SELECT cost.id, cost.owner_user_id AS ownerUserId, cost.logical_store_id AS logicalStoreId,",
            "       cost.product_master_id AS productMasterId, cost.product_variant_id AS productVariantId,",
            "       cost.partner_sku AS partnerSku, cost.barcode, cost.site_code AS siteCode,",
            "       cost.forwarder_code AS forwarderCode, cost.forwarder_name AS forwarderName,",
            "       cost.transport_mode AS transportMode, cost.route_code AS routeCode, cost.route_name AS routeName,",
            "       cost.service_code AS serviceCode, cost.service_name AS serviceName,",
            "       cost.current_history_id AS currentHistoryId, cost.source_type AS sourceType,",
            "       cost.cost_type AS costType, cost.fee_type AS feeType,",
            "       cost.cargo_category_code AS cargoCategoryCode, cost.cargo_category_name AS cargoCategoryName,",
            "       cost.charge_unit AS chargeUnit, cost.unit_cost_cny AS unitCostCny,",
            "       cost.total_cost_cny AS totalCostCny, cost.currency_code AS currencyCode,",
            "       cost.confidence_level AS confidenceLevel, cost.cost_occurred_at AS costOccurredAt,",
            "       cost.refreshed_at AS refreshedAt, cost.evidence_json AS evidenceJson",
            "FROM product_logistics_current_cost cost",
            "JOIN logical_store active_store ON active_store.id = cost.logical_store_id",
            " AND active_store.owner_user_id = cost.owner_user_id AND active_store.is_deleted = b'0'",
            "WHERE cost.owner_user_id = #{ownerUserId}",
            "  AND cost.partner_sku = #{partnerSku}",
            "  AND cost.site_code = #{siteCode}",
            "  AND cost.forwarder_code = #{forwarderCode}",
            "  AND cost.transport_mode = #{transportMode}",
            "  AND cost.is_deleted = b'0'",
            "ORDER BY cost.logical_store_id ASC, cost.refreshed_at DESC, cost.id DESC"
    })
    List<CurrentCostRow> listCurrentCostsFromActiveStores(
            @Param("ownerUserId") Long ownerUserId,
            @Param("partnerSku") String partnerSku,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode
    );
}
