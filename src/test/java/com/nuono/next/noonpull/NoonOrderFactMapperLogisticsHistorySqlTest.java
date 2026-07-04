package com.nuono.next.noonpull;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.NoonOrderFactMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class NoonOrderFactMapperLogisticsHistorySqlTest {

    @Test
    void orderLineFactMarksStoreProductLogisticsHistory() throws Exception {
        Method method = NoonOrderFactMapper.class.getMethod(
                "markProductSiteOfferLogisticsHistoryByOrderLineFact",
                NoonOrderLineFact.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("UPDATE product_site_offer pso")
                .contains("JOIN noon_order_line_fact order_line")
                .contains("order_line.source_system = 'noon_order_report'")
                .contains("JOIN logical_store_site lss")
                .contains("pso.logical_store_id = lss.logical_store_id")
                .contains("order_line.partner_sku")
                .contains("REGEXP '[0-9]-[0-9]+$'")
                .contains("REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '')")
                .contains("pso.logistics_history_source = 'NOON_ORDER_LINE_FACT'")
                .doesNotContain("official_warehouse_asn")
                .doesNotContain("procurement_fulfillment_balance")
                .doesNotContain("warehouse_shipping");
    }
}
