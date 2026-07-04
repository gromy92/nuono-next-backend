package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WarehouseDispatchMapperSqlTest {

    @Test
    void balanceSelectCarriesPurchaseOrderLogisticsQuoteGateState() {
        String sql = WarehouseDispatchMapper.BALANCE_SELECT.replaceAll("\\s+", " ");

        assertThat(sql).contains("LEFT JOIN procurement_purchase_order_logistics_quote_line quote");
        assertThat(sql).contains("quote.purchase_order_item_site_id = balance.purchase_order_item_site_id");
        assertThat(sql).contains("COALESCE(quote.quote_status, 'PENDING_QUOTE') AS logisticsQuoteStatus");
        assertThat(sql).contains("COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS logisticsShippingSubmitStatus");
        assertThat(sql).doesNotContain("<>");
    }

    @Test
    void balanceInsertClassifiesNewProductFromStoreProductLogisticsHistoryWithoutSiteSplit() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "upsertBalanceFromItemSite",
                Long.class,
                String.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("is_new_product");
        assertThat(sql).contains("LEFT JOIN ( SELECT logical_store_id, UPPER(TRIM(partner_sku)) AS partner_sku_key");
        assertThat(sql).contains("MAX(CASE WHEN logistics_has_history = b'1' THEN 1 ELSE 0 END) AS logistics_has_history");
        assertThat(sql).contains("FROM product_site_offer");
        assertThat(sql).contains("GROUP BY logical_store_id, UPPER(TRIM(partner_sku))");
        assertThat(sql).contains("pso_logistics.logical_store_id = site.logical_store_id");
        assertThat(sql).contains("pso_logistics.partner_sku_key = UPPER(TRIM(item.partner_sku))");
        assertThat(sql).contains("CASE WHEN COALESCE(pso_logistics.logistics_has_history, 0) = 1 THEN b'0' ELSE b'1' END");
        assertThat(sql).doesNotContain("UPPER(TRIM(pso_logistics.site_code))");
        assertThat(sql).doesNotContain("UPPER(TRIM(pso.site_code)) = UPPER(TRIM(site.site_code))");
    }

    @Test
    void handoffSuccessDoesNotMarkProductSiteOfferLogisticsHistory() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/infrastructure/mapper/WarehouseDispatchMapper.java"
        ));
        assertThat(source)
                .doesNotContain("markProductSiteOfferLogisticsHistoryByDispatchPlan")
                .doesNotContain("WAREHOUSE_DISPATCH_HANDOFF")
                .doesNotContain("logistics_history_source = 'WAREHOUSE_DISPATCH_HANDOFF'");
    }

    @Test
    void outboundOrderPackingGateCountsUnconfirmedOrUnsubmittedQuoteSources() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "countBlockingOutboundOrderLogisticsQuotes",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM warehouse_outbound_order_line_source source");
        assertThat(sql).contains("LEFT JOIN procurement_purchase_order_logistics_quote_line quote");
        assertThat(sql).contains("quote.purchase_order_item_site_id = source.purchase_order_item_site_id");
        assertThat(sql).contains("quote.id IS NULL");
        assertThat(sql).contains("quote.quote_status != 'CONFIRMED'");
        assertThat(sql).contains("quote.shipping_submit_status != 'SUBMITTED'");
    }

    @Test
    void receiptOrdersOnlyExposeSubmittedShippingOrders() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listReceiptRows",
                Long.class,
                Collection.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM procurement_shipping_order so");
        assertThat(sql).contains("so.shipping_submit_status IN ('SUBMITTED', 'PARTIAL_SUBMITTED')");
        assertThat(sql).doesNotContain("so.status IN ('DRAFT', 'SUBMITTED')");
    }
}
