package com.nuono.next.postsaleprofit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.PostSaleProfitMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class PostSaleProfitMapperSqlTest {

    @Test
    void financeSourceReaderAggregatesNoonFinanceFactsByItemAndSku() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "listFinanceSaleCandidates",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM noon_finance_transaction_fact finance")
                .contains("finance.owner_user_id = #{ownerUserId}")
                .contains("finance.store_code = #{storeCode}")
                .contains("finance.site_code = #{siteCode}")
                .contains("finance.transaction_date >= #{dateFrom}")
                .contains("finance.transaction_date <= #{dateTo}")
                .contains("SUM(finance.net_proceeds) AS net_proceeds_lcy")
                .contains("SUM(finance.referral_fee_including_vat) AS referral_fee_lcy")
                .contains("SUM(finance.fulfillment_logistics_fees_including_vat) AS fulfillment_fee_lcy")
                .contains("GROUP BY finance.item_nr, finance.order_nr,")
                .contains("COALESCE(NULLIF(finance.partner_sku, ''), finance_variant.partner_sku), finance.sku, finance.currency");
    }

    @Test
    void financeSourceReaderFallsBackToProductSiteOfferWhenPartnerSkuIsMissing() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "listFinanceSaleCandidates",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("COALESCE(NULLIF(finance.partner_sku, ''), finance_variant.partner_sku) AS partner_sku")
                .contains("LEFT JOIN logical_store_site finance_site")
                .contains("finance_site.store_code = finance.store_code")
                .contains("LEFT JOIN product_site_offer finance_offer")
                .contains("finance_offer.psku_code = SUBSTRING_INDEX(finance.sku, '-', 1)")
                .contains("LEFT JOIN product_variant finance_variant")
                .contains("finance_variant.id = finance_offer.variant_id")
                .contains("GROUP BY finance.item_nr, finance.order_nr, COALESCE(NULLIF(finance.partner_sku, ''), finance_variant.partner_sku), finance.sku, finance.currency");
    }

    @Test
    void orderSourceReaderUsesOrderLineFactsForSoldQuantityAndOrderTime() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "listOrderSaleCandidates",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM noon_order_line_fact order_line")
                .contains("order_line.owner_user_id = #{ownerUserId}")
                .contains("order_line.store_code = #{storeCode}")
                .contains("order_line.site_code = #{siteCode}")
                .contains("DATE(order_line.order_timestamp) >= #{dateFrom}")
                .contains("DATE(order_line.order_timestamp) <= #{dateTo}")
                .contains("COUNT(*) AS sold_quantity")
                .contains("GROUP BY order_line.item_nr, order_line.order_identity, order_line.partner_sku, order_line.sku");
    }

    @Test
    void purchaseBatchReaderMapsProjectCodeToStoreCodeAndUsesActualCountedCost() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "listPurchaseCostBatches",
                Long.class,
                String.class,
                String.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM procurement_ali1688_sku_purchase_batch batch")
                .contains("LEFT JOIN procurement_ali1688_sku_purchase_batch_source source")
                .contains("JOIN logical_store ls")
                .contains("JOIN logical_store_site lss")
                .contains("ls.project_code COLLATE utf8mb4_unicode_ci = batch.target_store_code COLLATE utf8mb4_unicode_ci")
                .contains("lss.store_code = #{storeCode}")
                .contains("lss.site = #{siteCode}")
                .contains("batch.counted_cost AS purchase_cost_cny")
                .contains("LEFT JOIN product_master manual_master")
                .contains("manual_master.title_cache AS product_title")
                .contains("manual_master.cover_image_url AS product_image_url")
                .contains("batch.counted_quantity AS purchase_quantity")
                .contains("batch.counted_cost / NULLIF(batch.counted_quantity, 0) AS purchase_unit_cost_cny")
                .contains("MIN(source.source_order_time)")
                .contains("AS purchase_batch_time")
                .contains("purchase.provider_order_no AS providerOrderNo")
                .doesNotContain("listing_price")
                .doesNotContain("product_price");
    }

    @Test
    void purchaseBatchReaderUsesAli1688SkuAllocationActualCostWhenManualBatchIsMissing() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "listPurchaseCostBatches",
                Long.class,
                String.class,
                String.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM procurement_ali1688_order_sku_allocation allocation")
                .contains("JOIN procurement_ali1688_order_header allocation_header")
                .contains("CONCAT('ALI1688_ALLOCATION:', allocation.order_id, ':',")
                .contains("SUM(CASE WHEN allocation.sku_quantity > 0 THEN allocation.sku_quantity ELSE 0 END) AS purchase_quantity")
                .contains("SUM(allocation.allocated_cost) AS purchase_cost_cny")
                .contains("SUM(allocation.allocated_cost) / NULLIF(SUM(CASE WHEN allocation.sku_quantity > 0 THEN allocation.sku_quantity ELSE 0 END), 0) AS purchase_unit_cost_cny")
                .contains("allocation.allocation_basis")
                .contains("allocation.evidence_text")
                .contains("LEFT JOIN product_master allocation_master")
                .contains("COALESCE(NULLIF(allocation.product_title, ''), NULLIF(allocation_master.title_cache, '')) AS product_title")
                .contains("COALESCE(NULLIF(allocation_master.cover_image_url, ''), NULLIF(allocation.product_image_url, '')) AS product_image_url")
                .contains("HAVING SUM(CASE WHEN allocation.sku_quantity > 0 THEN allocation.sku_quantity ELSE 0 END) > 0")
                .contains("allocation.target_store_code COLLATE utf8mb4_unicode_ci = ls.project_code COLLATE utf8mb4_unicode_ci")
                .contains("NOT EXISTS ( SELECT 1 FROM procurement_ali1688_sku_purchase_batch_source existing_source")
                .doesNotContain("source_unit_price AS purchase_unit_cost_cny")
                .doesNotContain("unit_price_text AS purchase_unit_cost_cny");
    }

    @Test
    void purchaseBatchReaderUsesAli1688ProductLinkActualOrderAmountWhenManualBatchAndAllocationAreMissing() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "listPurchaseCostBatches",
                Long.class,
                String.class,
                String.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM procurement_ali1688_order_item_product_link link")
                .contains("JOIN procurement_ali1688_order_item_assignment assignment")
                .contains("JOIN procurement_ali1688_order_item link_item")
                .contains("JOIN procurement_ali1688_order_header link_header")
                .contains("LEFT JOIN ( SELECT item_total.order_id")
                .contains("CONCAT('ALI1688_PRODUCT_LINK:', link.order_id, ':', link.assignment_id, ':',")
                .contains("'ALI1688_PRODUCT_LINK' AS source_type")
                .contains("SUM(assignment.assigned_quantity) AS purchase_quantity")
                .contains("link_item.amount_text")
                .contains("link_item.title")
                .contains("link_item.sku_text")
                .contains("link_item.model_text")
                .contains("link_item.image_url")
                .contains("link_header.paid_amount_text")
                .contains("link_header.goods_total_text")
                .contains("order_item_amount_total")
                .contains("paid_amount_allocated")
                .contains("item_amount_allocated")
                .contains("assignment.assigned_quantity / link_item.quantity")
                .contains("LEFT JOIN product_master link_master")
                .contains("COALESCE(NULLIF(link.product_title, ''), NULLIF(link_master.title_cache, '')) AS product_title")
                .contains("COALESCE(NULLIF(link_master.cover_image_url, ''), NULLIF(link.product_image_url, '')) AS product_image_url")
                .contains("link.target_store_code COLLATE utf8mb4_unicode_ci = ls.project_code COLLATE utf8mb4_unicode_ci")
                .contains("NOT EXISTS ( SELECT 1 FROM procurement_ali1688_sku_purchase_batch_source existing_source")
                .contains("NOT EXISTS ( SELECT 1 FROM procurement_ali1688_order_sku_allocation existing_allocation")
                .doesNotContain("link_item.unit_price_text AS purchase_unit_cost_cny");
    }

    @Test
    void headhaulReaderUsesActualHeadhaulComponentsAsSkuAverageWithInTransitEvidence() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "listHeadhaulCostBatches",
                Long.class,
                String.class,
                String.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM in_transit_freight_actual_component component")
                .contains("JOIN in_transit_freight_actual_bill bill")
                .contains("LEFT JOIN in_transit_batch freight_batch")
                .contains("CONCAT('IN_TRANSIT_HEADHAUL_SKU_AVERAGE:', component.target_site_code, ':', component.psku) COLLATE utf8mb4_unicode_ci AS source_id")
                .contains("NULL AS in_transit_batch_id")
                .contains("GROUP_CONCAT(DISTINCT bill.bill_no")
                .contains("GROUP_CONCAT(DISTINCT COALESCE(NULLIF(freight_batch.batch_reference_no, ''), CAST(component.batch_id AS CHAR))")
                .contains("GROUP_CONCAT(DISTINCT bill.transport_mode")
                .contains("GROUP_CONCAT(DISTINCT bill.destination_code")
                .contains("component.target_site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci")
                .contains("component.psku IS NOT NULL")
                .contains("component.psku <> ''")
                .contains("component.standard_fee_type = 'HEADHAUL'")
                .contains("SUM(component.cny_amount) AS headhaul_cost_cny")
                .contains("SUM(component.quantity) AS freight_quantity")
                .contains("SUM(headhaul_cost_cny) / NULLIF(SUM(freight_quantity), 0) AS headhaul_unit_cost_cny")
                .contains("batch_headhaul_components AS")
                .contains("batch_headhaul_allocations AS")
                .contains("freight_line.shipped_quantity / NULLIF(batch_totals.batch_shipped_quantity, 0)")
                .contains("'batch_headhaul_prorated_by_shipped_quantity' COLLATE utf8mb4_unicode_ci AS allocation_basis")
                .contains("UNION ALL")
                .contains("'actual_component_quantity' COLLATE utf8mb4_unicode_ci AS allocation_basis")
                .contains("'actual HEADHAUL components aggregated by psku + site; batch-level HEADHAUL bills are prorated by in-transit shipped quantity; purchase batch is not linked to in-transit batch' AS evidence_text")
                .contains("GROUP BY component.psku, component.target_site_code")
                .doesNotContain("goods.store_code COLLATE utf8mb4_unicode_ci = #{storeCode} COLLATE utf8mb4_unicode_ci");
    }

    @Test
    void batchListReaderReturnsProfitBreakdownFieldsForFrontend() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "listBatchRows",
                Long.class,
                PostSaleProfitBatchQuery.class,
                Integer.class,
                Integer.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM post_sale_profit_batch batch")
                .contains("COALESCE(batch.sku_parent, catalog_partner_master.sku_parent, catalog_offer_master.sku_parent) AS skuParent")
                .contains("NULLIF(catalog_partner_master.title_cn_cache, '')")
                .contains("NULLIF(catalog_offer_master.title_cache, '')")
                .contains("COALESCE(NULLIF(batch.image_url_snapshot, ''), catalog_partner_master.cover_image_url, catalog_offer_master.cover_image_url) AS productImageUrl")
                .contains("batch.purchase_batch_time AS purchaseBatchTime")
                .contains("batch.purchase_quantity AS purchaseQuantity")
                .contains("batch.purchase_unit_cost_cny AS purchaseUnitCostCny")
                .contains("batch.purchase_cost_cny AS purchaseCostCny")
                .contains("batch.shipping_source_type AS shippingSourceType")
                .contains("batch.shipping_source_id AS shippingSourceId")
                .contains("batch.shipping_batch_no AS shippingBatchNo")
                .contains("batch.in_transit_batch_id AS inTransitBatchId")
                .contains("batch.in_transit_reference_no AS inTransitReferenceNo")
                .contains("batch.available_at AS availableAt")
                .contains("batch.available_at_source AS availableAtSource")
                .contains("batch.headhaul_cost_source_type AS headhaulCostSourceType")
                .contains("batch.headhaul_unit_cost_cny AS headhaulUnitCostCny")
                .contains("batch.headhaul_cost_cny AS headhaulCostCny")
                .contains("batch.sold_quantity AS soldQuantity")
                .contains("batch.auto_quantity AS autoQuantity")
                .contains("batch.locked_quantity AS lockedQuantity")
                .contains("batch.net_proceeds_lcy AS netProceedsLcy")
                .contains("batch.referral_fee_lcy AS referralFeeLcy")
                .contains("batch.fulfillment_fee_lcy AS fulfillmentFeeLcy")
                .contains("batch.other_fee_net_lcy AS otherFeeNetLcy")
                .contains("sale_summary.average_sale_price_lcy AS averageSalePriceLcy")
                .contains("sale_summary.gmv_lcy AS gmvLcy")
                .contains("sale_summary.sale_price_fact_count AS salePriceFactCount")
                .contains("LEFT JOIN noon_order_line_fact order_line")
                .contains("order_line.item_nr COLLATE utf8mb4_unicode_ci = attr.item_nr COLLATE utf8mb4_unicode_ci")
                .contains("batch.currency AS currency")
                .contains("batch.fx_rate_to_cny AS fxRateToCny")
                .contains("batch.profit_cny AS profitCny")
                .contains("batch.profit_rate AS profitRate")
                .contains("batch.evidence_json AS evidenceJson");
    }

    @Test
    void latestRunReaderReturnsMostRecentActiveRunForStoreSite() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "selectLatestRun",
                Long.class,
                String.class,
                String.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM post_sale_profit_recalculation_run")
                .contains("owner_user_id = #{ownerUserId}")
                .contains("store_code = #{storeCode}")
                .contains("site_code = #{siteCode}")
                .contains("is_deleted = b'0'")
                .contains("date_from AS dateFrom")
                .contains("date_to AS dateTo")
                .contains("order_line_count AS orderLineCount")
                .contains("missing_issue_count AS missingIssueCount")
                .contains("ORDER BY CASE WHEN status = 'PREVIEW' THEN 0 ELSE 1 END, finished_at DESC, id DESC")
                .contains("LIMIT 1");
    }

    @Test
    void batchWriterPreservesAli1688AllocationPurchaseSourceType() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "insertBatch",
                com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.BatchWriteRow.class
        );

        String sql = insertSql(method);

        assertThat(sql)
                .contains("sku_parent, title_snapshot, image_url_snapshot")
                .contains("#{row.skuParent}, #{row.productTitle}, #{row.productImageUrl}")
                .contains("shipping_source_type, shipping_source_id, shipping_batch_no, in_transit_batch_id, in_transit_reference_no, available_at, available_at_source, headhaul_cost_source_type")
                .contains("#{row.shippingSourceType}, #{row.shippingSourceId}, #{row.shippingBatchNo}, #{row.inTransitBatchId}, #{row.inTransitReferenceNo}, #{row.availableAt}, #{row.availableAtSource}, #{row.headhaulCostSourceType}")
                .contains("referral_fee_lcy, fulfillment_fee_lcy, other_fee_net_lcy")
                .contains("#{row.referralFeeLcy}, #{row.fulfillmentFeeLcy}, #{row.otherFeeNetLcy}")
                .contains("WHEN #{row.sourceId} LIKE 'UNASSIGNED:%' THEN 'UNASSIGNED_ORDER_QUANTITY'")
                .contains("WHEN #{row.sourceId} LIKE 'ALI1688_ALLOCATION:%' THEN 'ALI1688_ALLOCATION'")
                .contains("WHEN #{row.sourceId} LIKE 'ALI1688_PRODUCT_LINK:%' THEN 'ALI1688_PRODUCT_LINK'")
                .contains("ELSE 'MANUAL_SKU_PURCHASE_BATCH'");
    }

    @Test
    void batchReaderOnlyMissingFilterUsesHardMissingStatuses() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "listBatchRows",
                Long.class,
                PostSaleProfitBatchQuery.class,
                Integer.class,
                Integer.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("MISSING_PURCHASE_COST")
                .contains("MISSING_HEADHAUL")
                .contains("MISSING_FX_RATE")
                .contains("UNASSIGNED_ORDER_QUANTITY")
                .doesNotContain("quality_status_json NOT LIKE '%OK%'");
    }

    @Test
    void lockedAttributionReaderUsesActivePreviewRowsBeforeRecalculationDeletesThem() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "listLockedAttributionsForScope",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM post_sale_profit_order_attribution attr")
                .contains("JOIN post_sale_profit_batch batch")
                .contains("JOIN post_sale_profit_recalculation_run run")
                .contains("batch.purchase_source_id AS sourceId")
                .contains("attr.attributed_quantity AS quantity")
                .contains("attr.manual_reason AS manualReason")
                .contains("attr.locked = b'1'")
                .contains("run.status = 'PREVIEW'")
                .contains("run.date_from = #{dateFrom}")
                .contains("run.date_to = #{dateTo}")
                .contains("run.is_deleted = b'0'")
                .contains("attr.is_deleted = b'0'")
                .contains("ORDER BY attr.order_time ASC, attr.id ASC");
    }

    @Test
    void softDeletePreviewRunsMutatesScopeHashToAvoidDeletedRunUniqueKeyCollisions() throws Exception {
        Method method = PostSaleProfitMapper.class.getMethod(
                "softDeletePreviewRuns",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class
        );

        String sql = updateSql(method);

        assertThat(sql)
                .contains("UPDATE post_sale_profit_recalculation_run")
                .contains("SET scope_hash = CONCAT(scope_hash, '|deleted|', id)")
                .contains("is_deleted = b'1'")
                .contains("status = 'PREVIEW'")
                .contains("is_deleted = b'0'");
    }

    private static String selectSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }

    private static String updateSql(Method method) {
        return String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");
    }

    private static String insertSql(Method method) {
        return String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");
    }
}
