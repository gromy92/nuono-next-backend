package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class OfficialWarehouseStatisticsMapperLogisticsHistorySqlTest {

    @Test
    void currentPositiveInventorySnapshotMarksStoreProductLogisticsHistory() throws Exception {
        Method method = OfficialWarehouseStatisticsMapper.class.getMethod(
                "markProductSiteOfferLogisticsHistoryByInventorySnapshotLine",
                Long.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("UPDATE product_site_offer pso")
                .contains("JOIN official_warehouse_inventory_snapshot_line line")
                .contains("line.is_current = b'1'")
                .contains("COALESCE(line.qty, 0) > 0")
                .contains("pso.logical_store_id = line.logical_store_id")
                .contains("CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4)")
                .contains("REGEXP '[0-9]-[0-9]+$'")
                .contains("REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '')")
                .contains("pso.logistics_has_history = b'1'")
                .contains("pso.logistics_history_source = 'OFFICIAL_WAREHOUSE_INVENTORY'")
                .doesNotContain("warehouse_shipping")
                .doesNotContain("procurement_fulfillment_balance");
    }
}
