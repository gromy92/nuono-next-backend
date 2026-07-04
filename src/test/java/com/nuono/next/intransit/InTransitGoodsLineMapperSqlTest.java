package com.nuono.next.intransit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.InTransitGoodsLineMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class InTransitGoodsLineMapperSqlTest {

    @Test
    void lineSaveMarksStoreProductLogisticsHistoryFromInTransitBatchOnly() throws Exception {
        Method method = InTransitGoodsLineMapper.class.getMethod(
                "markProductSiteOfferLogisticsHistoryByLine",
                Long.class,
                Long.class,
                Long.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("UPDATE product_site_offer pso")
                .contains("FROM in_transit_goods_line line")
                .contains("JOIN in_transit_batch batch")
                .contains("batch.batch_status")
                .contains("JOIN logical_store_site lss")
                .contains("LEFT JOIN product_barcode pb")
                .contains("LEFT JOIN product_variant pv")
                .contains("raw_owner_in_transit")
                .contains("line.sku")
                .contains("owner_unique_product")
                .contains("COUNT(DISTINCT owner_product.logical_store_id) = 1")
                .contains("owner_unique_product.partner_sku_key = raw_owner_in_transit.partner_sku_key")
                .contains("matched_history.product_site_offer_id")
                .contains("history.product_site_offer_id = pso.id")
                .contains("pso_match.id AS product_site_offer_id")
                .contains("COALESCE(pv.partner_sku, NULLIF(TRIM(line.psku), ''), NULLIF(TRIM(line.sku), ''))")
                .contains("REGEXP '[0-9]-[0-9]+$'")
                .contains("REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '')")
                .contains("pso.logistics_history_source = 'IN_TRANSIT_GOODS_LINE'")
                .doesNotContain("procurement_fulfillment_balance")
                .doesNotContain("warehouse_shipping")
                .doesNotContain("warehouse_packing")
                .doesNotContain("official_warehouse_asn")
                .doesNotContain("WAREHOUSE_DISPATCH_HANDOFF");
    }
}
