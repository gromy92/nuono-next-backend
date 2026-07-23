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

public interface WarehousePackingMapper extends WarehouseOutboundMapper {

@Insert({
            "INSERT INTO warehouse_packing_list (",
            "id, outbound_order_id, owner_user_id, packing_no, status, box_count, packed_quantity, gross_weight_kg, volume_cbm,",
            "remark, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.outboundOrderId}, #{row.ownerUserId}, #{row.packingNo}, #{row.status}, #{row.boxCount},",
            "#{row.packedQuantity}, #{row.grossWeightKg}, #{row.volumeCbm}, #{row.remark}, b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertPackingList(
            @Param("row") PackingListRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

@Update({
            "UPDATE warehouse_outbound_order",
            "SET status = 'PACKING',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{outboundOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status IN ('DRAFT', 'PACKING')",
            "  AND is_deleted = b'0'"
    })
    int markOutboundOrderPacking(
            @Param("outboundOrderId") Long outboundOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

@Select({
            "SELECT id, outbound_order_id AS outboundOrderId, owner_user_id AS ownerUserId, packing_no AS packingNo, status,",
            "       box_count AS boxCount, packed_quantity AS packedQuantity, gross_weight_kg AS grossWeightKg, volume_cbm AS volumeCbm,",
            "       remark, DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_packing_list",
            "WHERE id = #{packingListId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    PackingListRecord selectPackingListById(@Param("packingListId") Long packingListId);

@Select({
            "SELECT id, outbound_order_id AS outboundOrderId, owner_user_id AS ownerUserId, packing_no AS packingNo, status,",
            "       box_count AS boxCount, packed_quantity AS packedQuantity, gross_weight_kg AS grossWeightKg, volume_cbm AS volumeCbm,",
            "       remark, DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt, DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM warehouse_packing_list",
            "WHERE outbound_order_id = #{outboundOrderId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<PackingListRecord> listPackingListsByOutboundOrder(@Param("outboundOrderId") Long outboundOrderId);

@Delete({
            "DELETE FROM warehouse_packing_box_item",
            "WHERE packing_list_id = #{packingListId}"
    })
    int deletePackingBoxItems(
            @Param("packingListId") Long packingListId,
            @Param("operatorUserId") Long operatorUserId
    );

@Delete({
            "DELETE FROM warehouse_packing_box",
            "WHERE packing_list_id = #{packingListId}"
    })
    int deletePackingBoxes(
            @Param("packingListId") Long packingListId,
            @Param("operatorUserId") Long operatorUserId
    );

@Insert({
            "INSERT INTO warehouse_packing_box (",
            "id, packing_list_id, outbound_order_id, owner_user_id, box_no, status, length_cm, width_cm, height_cm, gross_weight_kg,",
            "quantity, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.packingListId}, #{row.outboundOrderId}, #{row.ownerUserId}, #{row.boxNo}, #{row.status}, #{row.lengthCm},",
            "#{row.widthCm}, #{row.heightCm}, #{row.grossWeightKg}, #{row.quantity}, b'0',",
            "#{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertPackingBox(
            @Param("row") PackingBoxRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

@Insert({
            "INSERT INTO warehouse_packing_box_item (",
            "id, packing_list_id, packing_box_id, outbound_order_id, outbound_order_line_id, owner_user_id, product_variant_id,",
            "partner_sku, site_code, actual_transport_mode, quantity, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.packingListId}, #{row.packingBoxId}, #{row.outboundOrderId}, #{row.outboundOrderLineId},",
            "#{row.ownerUserId}, #{row.productVariantId}, #{row.partnerSku}, #{row.siteCode}, #{row.actualTransportMode},",
            "#{row.quantity}, b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertPackingBoxItem(
            @Param("row") PackingBoxItemRecord row,
            @Param("operatorUserId") Long operatorUserId
    );

@Update({
            "UPDATE warehouse_packing_list",
            "SET box_count = #{boxCount},",
            "    packed_quantity = #{packedQuantity},",
            "    gross_weight_kg = #{grossWeightKg},",
            "    volume_cbm = #{volumeCbm},",
            "    remark = #{remark},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{packingListId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status = 'DRAFT'",
            "  AND is_deleted = b'0'"
    })
    int updatePackingListTotals(
            @Param("packingListId") Long packingListId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("boxCount") Integer boxCount,
            @Param("packedQuantity") Integer packedQuantity,
            @Param("grossWeightKg") java.math.BigDecimal grossWeightKg,
            @Param("volumeCbm") java.math.BigDecimal volumeCbm,
            @Param("remark") String remark,
            @Param("operatorUserId") Long operatorUserId
    );

@Select({
            "SELECT id, packing_list_id AS packingListId, outbound_order_id AS outboundOrderId, owner_user_id AS ownerUserId,",
            "       box_no AS boxNo, status, length_cm AS lengthCm, width_cm AS widthCm, height_cm AS heightCm,",
            "       gross_weight_kg AS grossWeightKg, quantity",
            "FROM warehouse_packing_box",
            "WHERE packing_list_id = #{packingListId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC"
    })
    List<PackingBoxRecord> listPackingBoxes(@Param("packingListId") Long packingListId);

@Select({
            "SELECT id, packing_list_id AS packingListId, packing_box_id AS packingBoxId, outbound_order_id AS outboundOrderId,",
            "       outbound_order_line_id AS outboundOrderLineId, owner_user_id AS ownerUserId, product_variant_id AS productVariantId,",
            "       partner_sku AS partnerSku, site_code AS siteCode, actual_transport_mode AS actualTransportMode, quantity",
            "FROM warehouse_packing_box_item",
            "WHERE packing_list_id = #{packingListId}",
            "  AND is_deleted = b'0'",
            "ORDER BY packing_box_id ASC, id ASC"
    })
    List<PackingBoxItemRecord> listPackingBoxItems(@Param("packingListId") Long packingListId);

@Update({
            "UPDATE warehouse_packing_list",
            "SET status = 'CONFIRMED',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{packingListId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status = 'DRAFT'",
            "  AND is_deleted = b'0'"
    })
    int confirmPackingList(
            @Param("packingListId") Long packingListId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

@Update({
            "UPDATE warehouse_outbound_order",
            "SET status = 'PACKED',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{outboundOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status IN ('DRAFT', 'PACKING')",
            "  AND is_deleted = b'0'"
    })
    int markOutboundOrderPacked(
            @Param("outboundOrderId") Long outboundOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

@Update({
            "UPDATE warehouse_packing_list",
            "SET status = 'SHIPPED',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{packingListId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status IN ('CONFIRMED', 'SEALED')",
            "  AND is_deleted = b'0'"
    })
    int shipPackingList(
            @Param("packingListId") Long packingListId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

@Update({
            "UPDATE warehouse_outbound_order",
            "SET status = 'SHIPPED',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{outboundOrderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND status IN ('PACKED', 'SHIPPED')",
            "  AND is_deleted = b'0'"
    })
    int markOutboundOrderShipped(
            @Param("outboundOrderId") Long outboundOrderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );
}
