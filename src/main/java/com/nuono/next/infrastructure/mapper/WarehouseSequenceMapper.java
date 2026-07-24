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

public interface WarehouseSequenceMapper {

String BALANCE_SELECT = ""
            + "SELECT balance.id, balance.owner_user_id AS ownerUserId, balance.logical_store_id AS logicalStoreId, "
            + "balance.source_store_code AS sourceStoreCode, balance.source_store_name AS sourceStoreName, "
            + "balance.purchase_order_id AS purchaseOrderId, balance.purchase_order_no AS purchaseOrderNo, "
            + "balance.purchase_order_title AS purchaseOrderTitle, balance.purchase_order_item_id AS purchaseOrderItemId, "
            + "balance.purchase_order_item_site_id AS purchaseOrderItemSiteId, balance.product_master_id AS productMasterId, "
            + "balance.product_variant_id AS productVariantId, balance.partner_sku AS partnerSku, balance.sku_parent AS skuParent, "
            + "balance.title_cache AS titleCache, balance.image_url_cache AS imageUrlCache, balance.site_code AS siteCode, "
            + "balance.is_new_product = b'1' AS isNewProduct, "
            + "balance.planned_transport_mode AS plannedTransportMode, balance.target_site_code AS targetSiteCode, "
            + "balance.target_transport_mode AS targetTransportMode, balance.fulfillment_type AS fulfillmentType, "
            + "balance.planned_quantity AS plannedQuantity, balance.confirmed_quantity AS confirmedQuantity, "
            + "balance.abnormal_quantity AS abnormalQuantity, balance.reserved_quantity AS reservedQuantity, "
            + "balance.logistics_handoff_quantity AS logisticsHandoffQuantity, balance.available_quantity AS availableQuantity, "
            + "CASE WHEN ali1688Spec.product_length_cm IS NULL "
            + "       OR ali1688Spec.product_width_cm IS NULL "
            + "       OR ali1688Spec.product_height_cm IS NULL "
            + "       OR ali1688Spec.product_weight_g IS NULL "
            + "     THEN 'SPEC_MISSING' ELSE 'READY' END AS specStatus, "
            + "ali1688Spec.product_length_cm AS productLengthCm, "
            + "ali1688Spec.product_width_cm AS productWidthCm, "
            + "ali1688Spec.product_height_cm AS productHeightCm, "
            + "ali1688Spec.product_weight_g AS productWeightG, "
            + "pvlp.profile_status AS logisticsProfileStatus, pvlp.battery_type AS batteryType, "
            + "pvlp.magnetic_type AS magneticType, pvlp.liquid_powder_type AS liquidPowderType, "
            + "pvlp.electric_type AS electricType, pvlp.blade_weapon_type AS bladeWeaponType, "
            + "pvlp.sensitive_tags_json AS sensitiveTagsJson, pvlp.manual_confirm_required = b'1' AS manualConfirmRequired, "
            + "COALESCE(quote.quote_status, 'PENDING_QUOTE') AS logisticsQuoteStatus, "
            + "COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS logisticsShippingSubmitStatus, "
            + "(quote.id IS NULL OR quote.quote_status != 'CONFIRMED' OR quote.shipping_submit_status != 'SUBMITTED') AS logisticsQuoteBlocking, "
            + "balance.status "
            + "FROM procurement_fulfillment_balance balance "
            + "LEFT JOIN product_variant_spec_source ali1688Spec "
            + "  ON ali1688Spec.variant_id = balance.product_variant_id "
            + " AND ali1688Spec.source_type = 'ali1688' "
            + " AND ali1688Spec.is_deleted = b'0' "
            + "LEFT JOIN product_variant_logistics_profile pvlp "
            + "  ON pvlp.variant_id = balance.product_variant_id "
            + " AND pvlp.is_deleted = b'0' "
            + "LEFT JOIN procurement_purchase_order_logistics_quote_line quote "
            + "  ON quote.purchase_order_item_site_id = balance.purchase_order_item_site_id "
            + " AND quote.is_deleted = b'0' ";

@Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

default Long nextId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("仓库发运 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

default Long nextConfirmationId() {
        return nextId("procurement_fulfillment_confirmation", 320000L);
    }

default Long nextConfirmationLineId() {
        return nextId("procurement_fulfillment_confirmation_line", 330000L);
    }

default Long nextDispatchPlanId() {
        return nextId("procurement_dispatch_plan", 340000L);
    }

default Long nextDispatchLineId() {
        return nextId("procurement_dispatch_plan_line", 350000L);
    }

default Long nextDispatchSourceId() {
        return nextId("procurement_dispatch_plan_line_source", 360000L);
    }

default Long nextOperationLogId() {
        return nextId("procurement_dispatch_plan_operation_log", 390000L);
    }

default Long nextShippingBatchId() {
        return nextId("warehouse_shipping_batch", 700000L);
    }

default Long nextShippingBatchSourceId() {
        return nextId("warehouse_shipping_batch_source", 710000L);
    }

default Long nextShippingSuggestionOptionId() {
        return nextId("warehouse_shipping_suggestion_option", 720000L);
    }

default Long nextShippingSuggestionLineId() {
        return nextId("warehouse_shipping_suggestion_line", 730000L);
    }

default Long nextShippingSuggestionLineSourceId() {
        return nextId("warehouse_shipping_suggestion_line_source", 740000L);
    }

default Long nextOutboundOrderId() {
        return nextId("warehouse_outbound_order", 800000L);
    }

default Long nextOutboundOrderLineId() {
        return nextId("warehouse_outbound_order_line", 810000L);
    }

default Long nextOutboundOrderLineSourceId() {
        return nextId("warehouse_outbound_order_line_source", 820000L);
    }

default Long nextPackingListId() {
        return nextId("warehouse_packing_list", 830000L);
    }

default Long nextPackingBoxId() {
        return nextId("warehouse_packing_box", 840000L);
    }

default Long nextPackingBoxItemId() {
        return nextId("warehouse_packing_box_item", 850000L);
    }
}
