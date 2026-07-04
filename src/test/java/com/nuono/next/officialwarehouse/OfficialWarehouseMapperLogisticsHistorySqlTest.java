package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class OfficialWarehouseMapperLogisticsHistorySqlTest {

    @Test
    void asnLinesMarkStoreProductLogisticsHistory() throws Exception {
        Method method = OfficialWarehouseMapper.class.getMethod(
                "markProductSiteOfferLogisticsHistoryByAsn",
                Long.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("UPDATE product_site_offer pso")
                .contains("JOIN official_warehouse_asn_line line")
                .contains("JOIN official_warehouse_asn asn")
                .contains("asn.status NOT IN ('DRAFT', 'FAILED')")
                .contains("line.line_status NOT IN ('PENDING', 'FAILED')")
                .contains("JOIN logical_store_site lss")
                .contains("pso.logical_store_id = lss.logical_store_id")
                .contains("REGEXP '[0-9]-[0-9]+$'")
                .contains("REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '')")
                .contains("pso.logistics_history_source = 'OFFICIAL_WAREHOUSE_ASN'")
                .doesNotContain("noon_order_line_fact")
                .doesNotContain("procurement_fulfillment_balance")
                .doesNotContain("warehouse_shipping");
    }
}
