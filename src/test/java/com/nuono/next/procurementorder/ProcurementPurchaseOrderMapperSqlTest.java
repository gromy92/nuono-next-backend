package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderItemRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductForwarderDeclarationAttributeRecord;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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
                Boolean.class,
                Boolean.class,
                Integer.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("ORDER BY po.gmt_create DESC, po.id DESC");
        assertThat(sql).doesNotContain("ORDER BY po.gmt_updated DESC");
    }

    @Test
    void listOrdersCanFilterSubmittedOrdersForShippingOrderSelection() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listOrdersByOwner",
                Long.class,
                java.util.Collection.class,
                String.class,
                Boolean.class,
                Boolean.class,
                Integer.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("<if test='submittedOnly != null and submittedOnly'>");
        assertThat(sql).contains("AND po.status = 'SUBMITTED'");
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
    void logisticsQuoteCandidatesJoinCurrentOrderItemSitesAndExistingQuoteRows() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listLogisticsQuoteCandidatesByOrder",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM procurement_purchase_order_item_site site");
        assertThat(sql).contains("JOIN procurement_purchase_order_item item");
        assertThat(sql).contains("LEFT JOIN procurement_purchase_order_logistics_quote_line quote");
        assertThat(sql).contains("quote.purchase_order_item_site_id = site.id");
        assertThat(sql).contains("LEFT JOIN procurement_fulfillment_balance balance");
        assertThat(sql).contains("LEFT JOIN product_variant_spec pvs");
        assertThat(sql).contains("LEFT JOIN product_public_detail_snapshot public_detail");
        assertThat(sql).contains("FROM product_barcode pb");
        assertThat(sql).contains("WHERE site.purchase_order_id = #{orderId}");
    }

    @Test
    void shippingOrderDetailLinesIncludeProductBarcodeSnapshot() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listShippingOrderLines",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("AS barcode");
        assertThat(sql).contains("FROM product_barcode pb");
        assertThat(sql).contains("pb.variant_id = sol.product_variant_id");
    }

    @Test
    void shippingOrderLogisticsQuoteCandidatesIncludeProductBarcodeSnapshot() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listLogisticsQuoteCandidatesByShippingOrder",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("AS barcode");
        assertThat(sql).contains("FROM product_barcode pb");
        assertThat(sql).contains("pb.variant_id = sol.product_variant_id");
    }

    @Test
    void currentProductForwarderChannelQuoteLookupUsesCurrentActiveQuote() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "selectCurrentProductForwarderChannelQuote",
                Long.class,
                String.class,
                Long.class,
                String.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM product_forwarder_channel_quote");
        assertThat(sql).contains("owner_user_id = #{ownerUserId}");
        assertThat(sql).contains("UPPER(partner_sku) = UPPER(#{partnerSku})");
        assertThat(sql).contains("logical_store_id IS NULL OR logical_store_id = #{logicalStoreId}");
        assertThat(sql).contains("source_store_code IS NULL OR TRIM(source_store_code) = '' OR UPPER(source_store_code) = UPPER(#{sourceStoreCode})");
        assertThat(sql).contains("product_variant_id = #{productVariantId}");
        assertThat(sql).contains("forwarder_code = #{forwarderCode}");
        assertThat(sql).contains("COALESCE(site_code, '') = COALESCE(#{siteCode}, '')");
        assertThat(sql).contains("effective_status = 'CURRENT'");
        assertThat(sql).contains("is_deleted = b'0'");
        assertThat(sql).contains("ORDER BY CASE");
        assertThat(sql).contains("TRIM(source_store_code) != ''");
        assertThat(sql).contains("UPPER(source_store_code) = UPPER(#{sourceStoreCode}) THEN 0");
        assertThat(sql).contains("confirmed_at DESC, id DESC");
        assertThat(sql).doesNotContain("TRIM(source_store_code) <>");
    }

    @Test
    void confirmLogisticsQuoteLinePreservesShippingSubmitState() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "confirmLogisticsQuoteLine",
                PurchaseOrderLogisticsQuoteLineRecord.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("shipping_submit_status = COALESCE(#{row.shippingSubmitStatus}, shipping_submit_status, 'NOT_SUBMITTED')");
        assertThat(sql).doesNotContain("shipping_submitted_at = NULL");
        assertThat(sql).doesNotContain("shipping_submitted_by = NULL");
    }

    @Test
    void shippingOrderSummaryQueriesExposeMissingYiteMaterialCount() throws Exception {
        Method detailMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "selectShippingOrderById",
                Long.class
        );
        Method listMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "listShippingOrders",
                Long.class,
                String.class,
                Integer.class
        );

        String detailSql = String.join(" ", detailMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        String listSql = String.join(" ", listMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(detailSql).contains("AS missingYiteMaterialCount");
        assertThat(detailSql).contains("FROM procurement_shipping_order_line sol");
        assertThat(detailSql).contains("sol.shipping_order_id = procurement_shipping_order.id");
        assertThat(detailSql).contains("sol.is_deleted = b'0'");
        assertThat(detailSql).contains("sol.yite_material IS NULL OR TRIM(sol.yite_material) = ''");
        assertThat(listSql).contains("AS missingYiteMaterialCount");
        assertThat(listSql).contains("FROM procurement_shipping_order_line sol");
        assertThat(listSql).contains("sol.shipping_order_id = procurement_shipping_order.id");
        assertThat(listSql).contains("sol.is_deleted = b'0'");
        assertThat(listSql).contains("sol.yite_material IS NULL OR TRIM(sol.yite_material) = ''");
    }

    @Test
    void logisticsBillListJoinsShippingOrderAndReconciliationState() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listLogisticsBills",
                Long.class,
                String.class,
                Integer.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM logistics_expected_bill bill");
        assertThat(sql).contains("LEFT JOIN procurement_shipping_order so");
        assertThat(sql).contains("LEFT JOIN logistics_bill_reconciliation reconciliation");
        assertThat(sql).contains("bill.owner_user_id = #{ownerUserId}");
        assertThat(sql).contains("bill.is_deleted = b'0'");
    }

    @Test
    void productForwarderDeclarationAttributeMigrationIsProductAndForwarderScoped() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/147_product_forwarder_declaration_attribute.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `product_forwarder_declaration_attribute`");
        assertThat(sql).contains("`owner_user_id` BIGINT NOT NULL");
        assertThat(sql).contains("`product_variant_id` BIGINT NOT NULL");
        assertThat(sql).contains("`forwarder_code` VARCHAR(80) NOT NULL");
        assertThat(sql).contains("`attribute_code` VARCHAR(80) NOT NULL");
        assertThat(sql).contains("`attribute_value` VARCHAR(200) DEFAULT NULL");
        assertThat(sql).contains("UNIQUE KEY `uk_product_forwarder_declaration_attribute_active`");
        assertThat(sql).contains("`owner_user_id`, `product_variant_id`, `forwarder_code`, `attribute_code`, `active_slot`");
        assertThat(sql).contains("'product_forwarder_declaration_attribute'");
    }

    @Test
    void productForwarderDeclarationAttributeMapperPersistsAndReadsActiveAttributes() throws Exception {
        Method listMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "listProductForwarderDeclarationAttributes",
                Long.class,
                String.class,
                String.class,
                java.util.List.class,
                java.util.List.class
        );
        Method upsertMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "upsertProductForwarderDeclarationAttribute",
                ProductForwarderDeclarationAttributeRecord.class,
                Long.class
        );
        Method deleteMethod = ProcurementPurchaseOrderMapper.class.getMethod(
                "softDeleteProductForwarderDeclarationAttribute",
                Long.class,
                String.class,
                Long.class,
                String.class,
                Long.class,
                String.class,
                String.class,
                Long.class
        );

        String listSql = String.join(" ", listMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        String upsertSql = String.join(" ", upsertMethod.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");
        String deleteSql = String.join(" ", deleteMethod.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(listSql).contains("FROM product_forwarder_declaration_attribute");
        assertThat(listSql).contains("owner_user_id = #{ownerUserId}");
        assertThat(listSql).contains("forwarder_code = #{forwarderCode}");
        assertThat(listSql).contains("attribute_code = #{attributeCode}");
        assertThat(listSql).contains("source_store_code AS sourceStoreCode");
        assertThat(listSql).contains("partner_sku AS partnerSku");
        assertThat(listSql).contains("UPPER(partner_sku) IN");
        assertThat(listSql).contains("product_variant_id IN");
        assertThat(listSql).contains("is_deleted = b'0'");
        assertThat(upsertSql).contains("INSERT INTO product_forwarder_declaration_attribute");
        assertThat(upsertSql).contains("logical_store_id, source_store_code, partner_sku");
        assertThat(upsertSql).contains("ON DUPLICATE KEY UPDATE");
        assertThat(upsertSql).contains("source_store_code = VALUES(source_store_code)");
        assertThat(upsertSql).contains("partner_sku = VALUES(partner_sku)");
        assertThat(upsertSql).contains("attribute_value = VALUES(attribute_value)");
        assertThat(upsertSql).contains("source_shipping_order_line_id = VALUES(source_shipping_order_line_id)");
        assertThat(deleteSql).contains("SET is_deleted = b'1'");
        assertThat(deleteSql).contains("owner_user_id = #{ownerUserId}");
        assertThat(deleteSql).contains("UPPER(partner_sku) = UPPER(#{partnerSku})");
        assertThat(deleteSql).contains("logical_store_id IS NULL OR logical_store_id = #{logicalStoreId}");
        assertThat(deleteSql).contains("source_store_code IS NULL OR TRIM(source_store_code) = '' OR UPPER(source_store_code) = UPPER(#{sourceStoreCode})");
        assertThat(deleteSql).contains("product_variant_id = #{productVariantId}");
        assertThat(deleteSql).contains("forwarder_code = #{forwarderCode}");
        assertThat(deleteSql).contains("attribute_code = #{attributeCode}");
    }

    @Test
    void assignLogisticsQuoteLineChannelDoesNotConfirmQuoteOrSubmitShipping() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "assignLogisticsQuoteLineChannel",
                com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("forwarder_code = #{row.forwarderCode}");
        assertThat(sql).contains("route_code = #{row.routeCode}");
        assertThat(sql).contains("service_code = #{row.serviceCode}");
        assertThat(sql).contains("quote_status != 'CONFIRMED'");
        assertThat(sql).doesNotContain("quote_status = 'CONFIRMED'");
        assertThat(sql).doesNotContain("shipping_submit_status = 'SUBMITTED'");
    }

    @Test
    void unconfirmedLogisticsQuoteCountRequiresConfirmedQuoteBeforeSubmittingShipping() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "countUnconfirmedLogisticsQuoteLines",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("LEFT JOIN procurement_purchase_order_logistics_quote_line quote");
        assertThat(sql).contains("quote.id IS NULL");
        assertThat(sql).contains("quote.quote_status != 'CONFIRMED'");
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
        assertThat(sql).contains("link.sku_parent IN");
        assertThat(sql).doesNotContain("link.psku_code IN");
    }

    @Test
    void orderAli1688HistoryRequestTreatsPskuAsPartnerSkuOnly() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/procurementorder/LocalDbProcurementPurchaseOrderService.java"));

        assertThat(service)
                .contains("addTrimmed(partnerSkus, item.partnerSku);")
                .doesNotContain("addTrimmed(partnerSkus, site.pskuCode);")
                .doesNotContain("firstText(partnerSku, pskuCode)");
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
        assertThat(sql).contains("batch.sku_parent IN");
        assertThat(sql).doesNotContain("batch.psku_code IN");
    }

    @Test
    void productArchiveMatchesTreatPskuAsPartnerSkuOnly() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listProductArchiveMatches",
                Long.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("pv.partner_sku = #{psku}")
                .doesNotContain("pso.psku_code = #{psku}")
                .doesNotContain("CASE WHEN pv.partner_sku = #{psku}");
    }
}
