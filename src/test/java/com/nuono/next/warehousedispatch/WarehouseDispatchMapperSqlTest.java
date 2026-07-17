package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class WarehouseDispatchMapperSqlTest {

    @Test
    void dispatchPlanListBatchSummaryFailsClosedWithoutLoadingCostDetails() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listLatestShippingBatchSummariesByDispatchPlanIds",
                Collection.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("COUNT(*) = 0");
        assertThat(sql).contains("source.product_weight_g IS NULL OR source.product_weight_g &lt;= 0");
        assertThat(sql).contains("source.product_length_cm IS NULL OR source.product_length_cm &lt;= 0");
        assertThat(sql).contains("THEN NULL");
        assertThat(sql).contains("COUNT(*) AS option_count");
        assertThat(sql).contains("latest.dispatch_plan_id = batch.dispatch_plan_id");
        assertThat(sql).doesNotContain("cost_snapshot_json");
    }

    @Test
    void balanceSelectCarriesPurchaseOrderLogisticsQuoteGateState() {
        String sql = WarehouseDispatchMapper.BALANCE_SELECT.replaceAll("\\s+", " ");

        assertThat(sql).contains("LEFT JOIN procurement_purchase_order_logistics_quote_line quote");
        assertThat(sql).contains("quote.id = (SELECT preferred_quote.id");
        assertThat(sql).contains("preferred_quote.purchase_order_item_site_id = balance.purchase_order_item_site_id");
        assertThat(sql).contains("preferred_quote.shipping_order_line_id IS NOT NULL");
        assertThat(sql).contains("preferred_quote.id DESC LIMIT 1");
        assertThat(sql).contains("COALESCE(quote.quote_status, 'PENDING_QUOTE') AS logisticsQuoteStatus");
        assertThat(sql).contains("COALESCE(quote.shipping_submit_status, 'NOT_SUBMITTED') AS logisticsShippingSubmitStatus");
        assertThat(sql).doesNotContain("<>");
    }

    @Test
    void balanceRowsUseFallbackButReceiptRowsRequireWarehouseSpec() throws Exception {
        String balanceSql = WarehouseDispatchMapper.BALANCE_SELECT.replaceAll("\\s+", " ");
        assertProductSpecFallback(balanceSql);
        assertThat(balanceSql).contains("COALESCE(warehouseSpec.product_length_cm, pvss.product_length_cm, ali1688Spec.product_length_cm, officialSpec.product_length_cm, pvs.product_length_cm) AS productLengthCm");
        assertThat(balanceSql).contains("CASE WHEN COALESCE(warehouseSpec.product_length_cm, pvss.product_length_cm, ali1688Spec.product_length_cm, officialSpec.product_length_cm, pvs.product_length_cm) IS NULL");

        Method receiptMethod = WarehouseDispatchMapper.class.getMethod(
                "listReceiptRows",
                Long.class,
                Collection.class,
                String.class
        );
        String receiptSql = String.join(" ", receiptMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertThat(receiptSql).contains("LEFT JOIN product_variant_spec_source warehouseSpec");
        assertThat(receiptSql).contains("CASE WHEN warehouseSpec.product_length_cm IS NULL");
        assertThat(receiptSql).contains("warehouseSpec.product_length_cm AS productLengthCm");
        assertThat(receiptSql).doesNotContain("LEFT JOIN product_variant_spec_source ali1688Spec");
        assertThat(receiptSql).doesNotContain("COALESCE(warehouseSpec.product_length_cm");
    }

    @Test
    void balanceInsertClassifiesNewProductFromStoreProductLogisticsHistoryWithoutSiteSplit() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "upsertBalanceFromItemSite",
                Long.class,
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
    void balanceInsertDuplicateUpdateQualifiesCurrentBalanceColumns() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "upsertBalanceFromItemSite",
                Long.class,
                Long.class,
                String.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("procurement_fulfillment_balance.confirmed_quantity = 0");
        assertThat(sql).contains("procurement_fulfillment_balance.abnormal_quantity = 0");
        assertThat(sql).contains("procurement_fulfillment_balance.reserved_quantity = 0");
        assertThat(sql).contains("procurement_fulfillment_balance.logistics_handoff_quantity = 0");
        assertThat(sql).contains("ELSE procurement_fulfillment_balance.is_new_product END");
    }

    @Test
    void balanceInsertUsesAllocatedIdInsteadOfSiteDerivedId() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/infrastructure/mapper/WarehouseDispatchMapper.java"
        ));

        assertThat(source).doesNotContain("site.id + 900000000");
        assertThat(source).contains("#{balanceId}");
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
    void outboundOrderPackingGateSkipsDispatchPlanOrdersAndChecksLegacyQuoteSources() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "countBlockingOutboundOrderLogisticsQuotes",
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM warehouse_outbound_order_line_source source");
        assertThat(sql).contains("JOIN warehouse_outbound_order outbound_order");
        assertThat(sql).contains("JOIN warehouse_shipping_batch batch");
        assertThat(sql).contains("batch.dispatch_plan_id IS NULL");
        assertThat(sql).contains("LEFT JOIN procurement_purchase_order_logistics_quote_line quote");
        assertThat(sql).contains("preferred_quote.purchase_order_item_site_id = source.purchase_order_item_site_id");
        assertThat(sql).contains("quote.id IS NULL");
        assertThat(sql).contains("quote.quote_status != 'CONFIRMED'");
        assertThat(sql).contains("quote.shipping_submit_status != 'SUBMITTED'");
    }

    @Test
    void outboundOrderLinesCarryAppPackingIdentitySnapshot() throws Exception {
        Method insertMethod = WarehouseDispatchMapper.class.getMethod(
                "insertOutboundOrderLine",
                WarehouseDispatchRecords.OutboundOrderLineRecord.class,
                Long.class
        );
        String insertSql = String.join(" ", insertMethod.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(insertSql).contains("logical_store_id");
        assertThat(insertSql).contains("source_store_code");
        assertThat(insertSql).contains("source_store_name");
        assertThat(insertSql).contains("#{row.logicalStoreId}");
        assertThat(insertSql).contains("#{row.sourceStoreCode}");

        Method selectMethod = WarehouseDispatchMapper.class.getMethod("listOutboundOrderLines", Long.class);
        String selectSql = String.join(" ", selectMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(selectSql).contains("logical_store_id AS logicalStoreId");
        assertThat(selectSql).contains("source_store_code AS sourceStoreCode");
        assertThat(selectSql).contains("source_store_name AS sourceStoreName");
    }

    @Test
    void outboundOrderLineSourcesCarrySourceStoreSnapshotForAppDetails() throws Exception {
        Method insertMethod = WarehouseDispatchMapper.class.getMethod(
                "insertOutboundOrderLineSource",
                WarehouseDispatchRecords.OutboundOrderLineSourceRecord.class,
                Long.class
        );
        String insertSql = String.join(" ", insertMethod.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(insertSql).contains("source_store_code");
        assertThat(insertSql).contains("source_store_name");
        assertThat(insertSql).contains("#{row.sourceStoreCode}");
        assertThat(insertSql).contains("#{row.sourceStoreName}");

        Method selectMethod = WarehouseDispatchMapper.class.getMethod("listOutboundOrderLineSources", Long.class);
        String selectSql = String.join(" ", selectMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(selectSql).contains("source_store_code AS sourceStoreCode");
        assertThat(selectSql).contains("source_store_name AS sourceStoreName");
    }

    @Test
    void packingBoxesCarryStatusAndSpecsForAppFlow() throws Exception {
        Method insertMethod = WarehouseDispatchMapper.class.getMethod(
                "insertPackingBox",
                WarehouseDispatchRecords.PackingBoxRecord.class,
                Long.class
        );
        String insertSql = String.join(" ", insertMethod.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");

        assertThat(insertSql).contains("status");
        assertThat(insertSql).contains("length_cm");
        assertThat(insertSql).contains("width_cm");
        assertThat(insertSql).contains("height_cm");
        assertThat(insertSql).contains("gross_weight_kg");
        assertThat(insertSql).contains("#{row.status}");

        Method selectMethod = WarehouseDispatchMapper.class.getMethod("listPackingBoxes", Long.class);
        String selectSql = String.join(" ", selectMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(selectSql).contains("status");
        assertThat(selectSql).contains("length_cm AS lengthCm");
        assertThat(selectSql).contains("gross_weight_kg AS grossWeightKg");
    }

    @Test
    void packingBoxesCanBeUpdatedIdempotentlyByExistingBoxId() throws Exception {
        Method updateMethod = WarehouseDispatchMapper.class.getMethod(
                "updatePackingBox",
                WarehouseDispatchRecords.PackingBoxRecord.class,
                Long.class
        );
        String updateSql = String.join(" ", updateMethod.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(updateSql).contains("UPDATE warehouse_packing_box");
        assertThat(updateSql).contains("status = #{row.status}");
        assertThat(updateSql).contains("length_cm = #{row.lengthCm}");
        assertThat(updateSql).contains("gross_weight_kg = #{row.grossWeightKg}");
        assertThat(updateSql).contains("WHERE id = #{row.id}");
        assertThat(updateSql).contains("AND packing_list_id = #{row.packingListId}");
        assertThat(updateSql).contains("AND is_deleted = b'0'");
    }

    @Test
    void packingListWritesLockPackingAndOutboundRows() throws Exception {
        Method packingListLockMethod = WarehouseDispatchMapper.class.getMethod(
                "selectPackingListByIdForUpdate",
                Long.class
        );
        String packingListLockSql = String.join(" ", packingListLockMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        Method outboundOrderLockMethod = WarehouseDispatchMapper.class.getMethod(
                "selectOutboundOrderByIdForUpdate",
                Long.class
        );
        String outboundOrderLockSql = String.join(" ", outboundOrderLockMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(packingListLockSql).contains("FROM warehouse_packing_list").contains("FOR UPDATE");
        assertThat(outboundOrderLockSql).contains("FROM warehouse_outbound_order").contains("FOR UPDATE");
    }

    private static void assertProductSpecFallback(String sql) {
        assertThat(sql)
                .contains("LEFT JOIN product_variant_spec_source pvss")
                .contains("LEFT JOIN product_variant_spec_source warehouseSpec")
                .contains("warehouseSpec.source_type = 'warehouse'")
                .contains("LEFT JOIN product_variant_spec_source ali1688Spec")
                .contains("ali1688Spec.source_type = 'ali1688'")
                .contains("LEFT JOIN product_variant_spec_source officialSpec")
                .contains("officialSpec.source_type = 'noon_official'");
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
        assertThat(sql).contains("LEFT JOIN procurement_shipping_order_segment segment");
        assertThat(sql).contains("so.shipping_submit_status = 'SUBMITTED'");
        assertThat(sql).doesNotContain("so.shipping_submit_status = 'PARTIAL_SUBMITTED'");
        assertThat(sql).doesNotContain("so.status IN ('DRAFT', 'SUBMITTED')");
    }

    @Test
    void readyInventoryOnlyExposesBalancesFromSubmittedShippingOrders() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listReadyBalances",
                Long.class,
                Collection.class,
                String.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("balance.available_quantity > 0");
        assertThat(sql).contains("EXISTS ( SELECT 1 FROM procurement_shipping_order_line submitted_sol");
        assertThat(sql).contains("submitted_sol.purchase_order_item_site_id = balance.purchase_order_item_site_id");
        assertThat(sql).contains("submitted_so.shipping_submit_status = 'SUBMITTED'");
        assertThat(sql).doesNotContain("submitted_so.shipping_submit_status = 'PARTIAL_SUBMITTED'");
    }

    @Test
    void dispatchSelectionLocksOnlyBalancesFromSubmittedShippingOrders() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod("selectBalancesForUpdate", java.util.List.class);

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("WHERE balance.id IN");
        assertThat(sql).contains("EXISTS ( SELECT 1 FROM procurement_shipping_order_line submitted_sol");
        assertThat(sql).contains("submitted_sol.purchase_order_item_site_id = balance.purchase_order_item_site_id");
        assertThat(sql).contains("submitted_so.shipping_submit_status = 'SUBMITTED'");
        assertThat(sql).doesNotContain("submitted_segment");
        assertThat(sql).contains("FOR UPDATE");
    }

    @Test
    void dispatchQuoteBlockingKeepsZdExemptionAfterWarehouseSubmission() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod("selectBalancesForUpdate", java.util.List.class);

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("quote.shipping_submit_status != 'SUBMITTED'");
        assertThat(sql).contains("UPPER(COALESCE(quote.forwarder_code, '')) = 'ZD'");
        assertThat(sql).contains("UPPER(COALESCE(quote.route_code, '')) LIKE 'ZD-%'");
        assertThat(sql).contains("quote.quote_status != 'CONFIRMED' AND NOT");
    }

    @Test
    void dispatchPlansPersistAndLookupClientIdempotencyKey() throws Exception {
        Method insertMethod = WarehouseDispatchMapper.class.getMethod(
                "insertDispatchPlan",
                WarehouseDispatchRecords.DispatchPlanRecord.class,
                Long.class
        );
        String insertSql = String.join(" ", insertMethod.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");
        assertThat(insertSql).contains("client_request_id");
        assertThat(insertSql).contains("request_fingerprint");
        assertThat(insertSql).contains("#{row.clientRequestId}");

        Method selectMethod = WarehouseDispatchMapper.class.getMethod(
                "selectDispatchPlanByClientRequestId",
                Long.class,
                String.class
        );
        String selectSql = String.join(" ", selectMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertThat(selectSql).contains("owner_user_id = #{ownerUserId}");
        assertThat(selectSql).contains("client_request_id = #{clientRequestId}");
    }

    @Test
    void receiptRowsUseStructuredPlanClosedQuantityForPendingReceipt() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listReceiptRows",
                Long.class,
                Collection.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("receipt_progress.plan_closed_quantity");
        assertThat(sql).contains("JSON_TABLE");
        assertThat(sql).contains("$.allocation[*]");
        assertThat(sql).contains("balanceId BIGINT PATH '$.balanceId'");
        assertThat(sql).contains("progress.balance_id AS fulfillment_balance_id");
        assertThat(sql).contains("receipt_progress.fulfillment_balance_id = balance.id");
        assertThat(sql).contains("$.planClosedQuantity");
        assertThat(sql).contains("planClosedDelta INT PATH '$.planClosedDelta' NULL ON EMPTY");
        assertThat(sql).doesNotContain("$.replenishmentQuantity') AS UNSIGNED) + CAST(JSON_UNQUOTE");
        assertThat(sql).doesNotContain("receipt_progress.purchase_order_item_id = item.id");
    }

    @Test
    void receiptConfirmationsPersistAndLookupClientIdempotencyKey() throws Exception {
        Method insertMethod = WarehouseDispatchMapper.class.getMethod(
                "insertConfirmation",
                WarehouseDispatchRecords.FulfillmentConfirmationInsertRecord.class
        );
        String insertSql = String.join(" ", insertMethod.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");
        assertThat(insertSql).contains("client_request_id");
        assertThat(insertSql).contains("request_fingerprint");
        assertThat(insertSql).contains("#{row.clientRequestId}");

        Method selectMethod = WarehouseDispatchMapper.class.getMethod(
                "selectConfirmationByClientRequestId",
                Long.class,
                String.class
        );
        String selectSql = String.join(" ", selectMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertThat(selectSql).contains("owner_user_id = #{ownerUserId}");
        assertThat(selectSql).contains("client_request_id = #{clientRequestId}");

        Method progressMethod = WarehouseDispatchMapper.class.getMethod(
                "listReceiptPlanClosedQuantities",
                List.class
        );
        String progressSql = String.join(" ", progressMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertThat(progressSql).contains("allocation.planClosedDelta");
        assertThat(progressSql).contains("GROUP BY allocation.balanceId");
    }

    @Test
    void receiptRowsExposeSourceBalanceIdentityForAppConfirmation() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listReceiptRows",
                Long.class,
                Collection.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("sol.purchase_order_item_site_id AS purchaseOrderItemSiteId");
        assertThat(sql).contains("balance.id AS fulfillmentBalanceId");
        assertThat(sql).contains("po.anchor_store_code_cache AS sourceStoreCode");
        assertThat(sql).contains("item.product_variant_id AS productVariantId");
        assertThat(sql).contains("warehouseSpec.product_length_cm AS productLengthCm");
        assertThat(sql).contains("warehouseSpec.product_width_cm AS productWidthCm");
        assertThat(sql).contains("warehouseSpec.product_height_cm AS productHeightCm");
        assertThat(sql).contains("warehouseSpec.product_weight_g AS productWeightG");
        assertThat(sql).contains("warehouseSpec.product_length_cm &lt;= 0");
        assertThat(sql).doesNotContain("warehouseSpec.product_length_cm <= 0");
        assertThat(sql).doesNotContain("COALESCE(warehouseSpec.product_length_cm");
    }

    @Test
    void routeCostComponentsExposeIncludedInBasePriceAsNumericBoolean() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listForwarderRouteCostComponents",
                Collection.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("CAST(0 AS UNSIGNED) AS includedInBasePrice");
        assertThat(sql).contains("CASE WHEN fee.included_in_base_price = b'1' THEN 1 ELSE 0 END AS includedInBasePrice");
        assertThat(sql).doesNotContain("b'0' AS includedInBasePrice");
        assertThat(sql).doesNotContain("fee.included_in_base_price AS includedInBasePrice");
    }

    @Test
    void shippingOptionsReadActivePurchaseOrderRoutesBySiteAndTransportMode() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "listActivePurchaseOrderRoutes",
                Collection.class,
                Collection.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM forwarder_quote_route_template route");
        assertThat(sql).contains("route.active_for_purchase_order = b'1'");
        assertThat(sql).contains("UPPER(route.site_code) IN");
        assertThat(sql).contains("UPPER(route.transport_mode) IN");
        assertThat(sql).contains("route.forwarder_code AS forwarderCode");
        assertThat(sql).doesNotContain("QIKE-SAU-AIR");
        assertThat(sql).doesNotContain("ET-SAU-AIR");
    }

    @Test
    void shippingBatchSqlPersistsAndReadsDispatchPlanLink() throws Exception {
        Method insertMethod = WarehouseDispatchMapper.class.getMethod(
                "insertShippingBatch",
                WarehouseDispatchRecords.ShippingBatchRecord.class,
                Long.class
        );
        String insertSql = String.join(" ", insertMethod.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");
        assertThat(insertSql).contains("dispatch_plan_id");
        assertThat(insertSql).contains("#{row.dispatchPlanId}");

        Method selectMethod = WarehouseDispatchMapper.class.getMethod("selectLatestShippingBatchByDispatchPlan", Long.class);
        String selectSql = String.join(" ", selectMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
        assertThat(selectSql).contains("dispatch_plan_id = #{dispatchPlanId}");
        assertThat(selectSql).contains("ORDER BY gmt_updated DESC, id DESC");
    }

    @Test
    void atomicShippingIssueLocksTheBatchRow() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod("selectShippingBatchByIdForUpdate", Long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM warehouse_shipping_batch");
        assertThat(sql).contains("WHERE id = #{batchId}");
        assertThat(sql).contains("FOR UPDATE");
    }
}
