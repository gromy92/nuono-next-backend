package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemRecord;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProcurementPurchaseOrderMapperSqlTest {

    @Test
    void listOrdersSortsByPurchaseOrderCreateTimeNewestFirst() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listOrders",
                Long.class,
                String.class,
                Integer.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("ORDER BY po.gmt_create DESC, po.id DESC");
        assertThat(sql).doesNotContain("ORDER BY po.gmt_updated DESC");
    }

    @Test
    void insertItemPersistsPurchaseOrderFulfillmentFields() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "insertItem",
                PurchaseOrderItemRecord.class
        );

        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("fulfillment_type");
        assertThat(sql).contains("fulfillment_source_name");
        assertThat(sql).contains("#{row.fulfillmentType}");
        assertThat(sql).contains("#{row.fulfillmentSourceName}");
    }

    @Test
    void airForwarderRecommendationCandidatesUseAirServiceLinesOnly() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listAirForwarderRecommendationCandidates",
                java.util.List.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("WHERE line.transport_mode = 'AIR'");
        assertThat(sql).doesNotContain("line.transport_mode = 'EXPRESS'");
        assertThat(sql).doesNotContain("NOT LIKE 'ET-SAU-CARGO-AIR-WH-%'");
    }

    @Test
    void routeRecommendationCandidatesReadRouteTemplates() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listRouteRecommendationCandidates",
                java.util.List.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM forwarder_quote_route_template route");
        assertThat(sql).contains("JOIN forwarder_quote_route_template_segment segment");
        assertThat(sql).contains("segment.segment_role = 'HEADHAUL'");
        assertThat(sql).contains("JOIN forwarder_quote_service_line line");
        assertThat(sql).contains("LEFT JOIN forwarder_quote_base_price price");
        assertThat(sql).contains("route.active_for_purchase_order = b'1'");
    }

    @Test
    void routeSupplementFeeQueriesReadWarehouseAndLastMileFees() throws Exception {
        Method basePriceMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "listBasePricesByServiceCodes",
                java.util.List.class
        );
        Method warehouseMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "listWarehouseProcessingFeesByServiceCodes",
                java.util.List.class
        );
        Method transportMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "listTransportFeesByServiceCodes",
                java.util.List.class
        );

        String basePriceSql = String.join(" ", basePriceMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        String warehouseSql = String.join(" ", warehouseMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        String transportSql = String.join(" ", transportMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(basePriceSql).contains("FROM forwarder_quote_base_price");
        assertThat(warehouseSql).contains("FROM forwarder_warehouse_processing_fee");
        assertThat(transportSql).contains("FROM forwarder_quote_transport_fee");
    }

    @Test
    void softDeleteLogisticsRecommendationsByOrderKeepsSupersededRecommendationsOutOfCurrentQueries() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "softDeleteLogisticsRecommendationsByOrder",
                Long.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("UPDATE procurement_purchase_order_logistics_recommendation");
        assertThat(sql).contains("SET is_deleted = b'1'");
        assertThat(sql).contains("WHERE purchase_order_id = #{orderId}");
        assertThat(sql).contains("AND is_deleted = b'0'");
    }

    @Test
    void orderAli1688HistoryRowsFilterByOrderProjectSitesAndSkus() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listOrderAli1688HistoryRows",
                Long.class,
                String.class,
                java.util.List.class,
                java.util.List.class,
                java.util.List.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM procurement_ali1688_order_item_product_link link");
        assertThat(sql).contains("link.target_store_code = #{projectCode}");
        assertThat(sql).contains("<foreach collection='siteCodes'");
        assertThat(sql).contains("link.partner_sku IN");
        assertThat(sql).contains("link.psku_code IN");
        assertThat(sql).contains("link.sku_parent IN");
    }

    @Test
    void orderAli1688PurchaseBatchesReturnSourcesForCurrentOrderSkus() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listOrderAli1688PurchaseBatches",
                Long.class,
                String.class,
                java.util.List.class,
                java.util.List.class,
                java.util.List.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM procurement_ali1688_sku_purchase_batch batch");
        assertThat(sql).contains("LEFT JOIN procurement_ali1688_sku_purchase_batch_source source");
        assertThat(sql).contains("batch.target_store_code = #{projectCode}");
        assertThat(sql).contains("<foreach collection='siteCodes'");
        assertThat(sql).contains("batch.partner_sku IN");
        assertThat(sql).contains("batch.psku_code IN");
        assertThat(sql).contains("batch.sku_parent IN");
    }
}
