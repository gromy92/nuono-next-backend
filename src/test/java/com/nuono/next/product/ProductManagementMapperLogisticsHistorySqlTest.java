package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductManagementMapperLogisticsHistorySqlTest {

    @Test
    void positiveSiteOfferStockMarksStoreProductLogisticsHistory() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "markProductSiteOfferLogisticsHistoryByStock",
                Long.class,
                String.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("UPDATE product_site_offer pso")
                .contains("logical_store_id = #{logicalStoreId}")
                .contains("CONVERT(UPPER(TRIM(stock_offer.partner_sku)) USING utf8mb4)")
                .contains("CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4)")
                .contains("REGEXP '[0-9]-[0-9]+$'")
                .contains("REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '')")
                .contains("COALESCE(stock_offer.fbn_stock, 0) + COALESCE(stock_offer.supermall_stock, 0) + COALESCE(stock_offer.fbp_stock, 0) > 0")
                .contains("pso.logistics_has_history = b'1'")
                .contains("pso.logistics_history_source = 'PRODUCT_SITE_OFFER_STOCK'")
                .doesNotContain("site_code = #{siteCode}")
                .doesNotContain("warehouse_shipping")
                .doesNotContain("procurement_fulfillment_balance");
    }
}
