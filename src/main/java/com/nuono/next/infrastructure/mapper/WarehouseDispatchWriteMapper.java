package com.nuono.next.infrastructure.mapper;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.BalanceQuantityDelta;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ForwarderRouteQuoteRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentConfirmationInsertRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentConfirmationLineInsertRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.IdSequenceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderAccessRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemSiteRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseReceiptRow;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface WarehouseDispatchWriteMapper extends WarehouseReceiptMapper {

@Select({
            "<script>",
            BALANCE_SELECT,
            "WHERE balance.id IN",
            "<foreach collection='balanceIds' item='balanceId' open='(' separator=',' close=')'>#{balanceId}</foreach>",
            "  AND balance.is_deleted = b'0'",
            "ORDER BY balance.id ASC",
            "FOR UPDATE",
            "</script>"
    })
    List<FulfillmentBalanceRecord> selectBalancesForUpdate(@Param("balanceIds") List<Long> balanceIds);

@Insert({
            "INSERT INTO procurement_dispatch_plan (",
            "id, owner_user_id, plan_no, status, item_count, sku_count, total_quantity, site_summary_json, transport_summary_json,",
            "remark, handoff_generation_no, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.planNo}, #{row.status}, #{row.itemCount}, #{row.skuCount}, #{row.totalQuantity},",
            "#{row.siteSummaryJson}, #{row.transportSummaryJson}, #{row.remark}, #{row.handoffGenerationNo}, b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertDispatchPlan(
            @Param("row") DispatchPlanRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

@Insert({
            "INSERT INTO procurement_dispatch_plan_line (",
            "id, dispatch_plan_id, owner_user_id, product_master_id, product_variant_id, partner_sku, sku_parent, title_cache,",
            "image_url_cache, site_code, actual_transport_mode, fulfillment_type, quantity, source_count,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.dispatchPlanId}, #{row.ownerUserId}, #{row.productMasterId}, #{row.productVariantId},",
            "#{row.partnerSku}, #{row.skuParent}, #{row.titleCache}, #{row.imageUrlCache}, #{row.siteCode},",
            "#{row.actualTransportMode}, #{row.fulfillmentType}, #{row.quantity}, #{row.sourceCount},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertDispatchPlanLine(
            @Param("row") DispatchPlanLineRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

@Insert({
            "INSERT INTO procurement_dispatch_plan_line_source (",
            "id, dispatch_plan_id, dispatch_plan_line_id, owner_user_id, fulfillment_balance_id, source_store_code, source_store_name,",
            "purchase_order_id, purchase_order_no, purchase_order_item_id, purchase_order_item_site_id, planned_transport_mode,",
            "fulfillment_type, quantity, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.dispatchPlanId}, #{row.dispatchPlanLineId}, #{row.ownerUserId}, #{row.fulfillmentBalanceId},",
            "#{row.sourceStoreCode}, #{row.sourceStoreName}, #{row.purchaseOrderId}, #{row.purchaseOrderNo},",
            "#{row.purchaseOrderItemId}, #{row.purchaseOrderItemSiteId}, #{row.plannedTransportMode}, #{row.fulfillmentType},",
            "#{row.quantity}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertDispatchPlanLineSource(
            @Param("row") DispatchPlanLineSourceRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

@Update({
            "UPDATE procurement_fulfillment_balance",
            "SET reserved_quantity = reserved_quantity + #{quantity},",
            "    available_quantity = available_quantity - #{quantity},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{balanceId}",
            "  AND is_deleted = b'0'",
            "  AND available_quantity >= #{quantity}"
    })
    int reserveBalance(
            @Param("balanceId") Long balanceId,
            @Param("quantity") Integer quantity,
            @Param("operatorUserId") Long operatorUserId
    );
}
