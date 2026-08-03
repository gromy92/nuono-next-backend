package com.nuono.next.infrastructure.mapper;

import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryRecords.ShippingBatchRawLineRecord;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OfficialWarehouseBatchSummaryMapper {

    @Select({
            "<script>",
            "SELECT b.id AS batchId, line.id AS goodsLineId,",
            "       line.psku, line.sku, line.msku,",
            "       COALESCE(NULLIF(line.product_name, ''), NULLIF(line.sku, ''),",
            "                NULLIF(line.psku, ''), NULLIF(line.msku, '')) AS title,",
            "       GREATEST(COALESCE(line.shipped_quantity, 0), 0) AS quantity",
            "FROM in_transit_batch b",
            "JOIN in_transit_goods_line line",
            "  ON line.batch_id = b.id",
            " AND line.owner_user_id = b.owner_user_id",
            " AND line.is_deleted = b'0'",
            "WHERE b.owner_user_id = #{ownerUserId}",
            "  AND b.is_deleted = b'0'",
            "  AND b.id IN",
            "  <foreach item='batchId' collection='batchIds' open='(' separator=',' close=')'>",
            "    #{batchId}",
            "  </foreach>",
            "  AND (",
            "    b.batch_status IN ('shipped', 'in_transit', 'customs_clearance', 'delivering', 'warehouse_received')",
            "    OR b.latest_node_status IN ('departed_origin', 'in_transit', 'arrived_port',",
            "                                'customs_clearance', 'customs_released', 'delivering',",
            "                                'warehouse_received')",
            "  )",
            "  AND GREATEST(COALESCE(line.shipped_quantity, 0), 0) &gt; 0",
            "ORDER BY b.id ASC, line.id ASC",
            "</script>"
    })
    List<ShippingBatchRawLineRecord> listRawLines(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchIds") Collection<Long> batchIds
    );
}
