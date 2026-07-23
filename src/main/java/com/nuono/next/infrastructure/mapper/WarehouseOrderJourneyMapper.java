package com.nuono.next.infrastructure.mapper;

import com.nuono.next.warehousedispatch.WarehouseOrderJourneyView;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WarehouseOrderJourneyMapper {

    String EXECUTION_STATUS = "CASE "
            + "WHEN EXISTS (SELECT 1 FROM warehouse_outbound_order shipped_order "
            + "WHERE shipped_order.batch_id = batch.id AND shipped_order.is_deleted = b'0') "
            + "AND NOT EXISTS (SELECT 1 FROM warehouse_outbound_order pending_order "
            + "WHERE pending_order.batch_id = batch.id AND pending_order.is_deleted = b'0' "
            + "AND pending_order.status != 'SHIPPED') THEN 'SHIPPED' "
            + "WHEN EXISTS (SELECT 1 FROM warehouse_outbound_order packed_order "
            + "WHERE packed_order.batch_id = batch.id AND packed_order.is_deleted = b'0') "
            + "AND NOT EXISTS (SELECT 1 FROM warehouse_outbound_order unpacked_order "
            + "WHERE unpacked_order.batch_id = batch.id AND unpacked_order.is_deleted = b'0' "
            + "AND unpacked_order.status NOT IN ('PACKED', 'SHIPPED')) THEN 'PACKED' "
            + "WHEN EXISTS (SELECT 1 FROM warehouse_outbound_order packing_order "
            + "WHERE packing_order.batch_id = batch.id AND packing_order.is_deleted = b'0' "
            + "AND packing_order.status = 'PACKING') THEN 'PACKING' "
            + "ELSE batch.status END";

    @Select({
            "<script>",
            "SELECT DISTINCT CAST(shipping_order.id AS CHAR) AS warehouseOrderId,",
            "       CAST(batch.id AS CHAR) AS shippingBatchId, batch.batch_no AS shippingBatchNo,",
            "       " + EXECUTION_STATUS + " AS status,",
            "       DATE_FORMAT(batch.gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM procurement_shipping_order shipping_order",
            "JOIN procurement_shipping_order_line shipping_line",
            "  ON shipping_line.shipping_order_id = shipping_order.id AND shipping_line.is_deleted = b'0'",
            "JOIN warehouse_shipping_batch_source batch_source",
            "  ON batch_source.purchase_order_item_site_id = shipping_line.purchase_order_item_site_id",
            " AND batch_source.owner_user_id = #{ownerUserId} AND batch_source.is_deleted = b'0'",
            "JOIN warehouse_shipping_batch batch",
            "  ON batch.id = batch_source.batch_id AND batch.owner_user_id = #{ownerUserId}",
            " AND batch.is_deleted = b'0'",
            "WHERE shipping_order.owner_user_id = #{ownerUserId}",
            "  AND shipping_order.is_deleted = b'0'",
            "<if test='storeCodes != null and storeCodes.size() &gt; 0'>",
            "  AND shipping_line.source_store_code IN",
            "  <foreach collection='storeCodes' item='storeCode' open='(' separator=',' close=')'>",
            "    #{storeCode}",
            "  </foreach>",
            "</if>",
            "ORDER BY updatedAt DESC, shippingBatchId DESC",
            "</script>"
    })
    List<WarehouseOrderJourneyView> listWarehouseOrderJourneys(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCodes") Collection<String> storeCodes
    );
}
