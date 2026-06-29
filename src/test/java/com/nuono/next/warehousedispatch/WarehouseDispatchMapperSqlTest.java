package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import java.util.Collection;
import java.lang.reflect.Method;
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
