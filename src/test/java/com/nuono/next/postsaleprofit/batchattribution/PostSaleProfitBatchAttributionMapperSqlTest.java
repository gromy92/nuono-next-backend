package com.nuono.next.postsaleprofit.batchattribution;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.PostSaleProfitBatchAttributionMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class PostSaleProfitBatchAttributionMapperSqlTest {

    @Test
    void skuRowReaderBuildsCandidatePoolFromCurrentStockPurchaseInTransitAsnBridgeAndSoldBatches() throws Exception {
        Method method = PostSaleProfitBatchAttributionMapper.class.getMethod(
                "listSkuRows",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                String.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("stock AS")
                .contains("FROM official_warehouse_inventory_snapshot_line stock_line")
                .contains("stock_line.is_current = b'1'")
                .contains("stock_line.qty > 0")
                .contains("sellable_stock_quantity")
                .contains("latest_run AS")
                .contains("FROM post_sale_profit_recalculation_run run")
                .contains("sold AS")
                .contains("FROM post_sale_profit_batch batch")
                .contains("purchase AS")
                .contains("FROM procurement_purchase_order_item_site site")
                .contains("JOIN procurement_purchase_order_item item")
                .contains("JOIN procurement_purchase_order po")
                .contains("in_transit AS")
                .contains("FROM in_transit_goods_line line")
                .contains("JOIN in_transit_batch in_batch")
                .contains("asn AS")
                .contains("FROM official_warehouse_asn_line asn_line")
                .contains("JOIN official_warehouse_asn asn")
                .contains("asn.status = 'LINES_CREATED'")
                .contains("asn.noon_asn_status IN ('grn_completed', 'sealed')")
                .contains("bridge AS")
                .contains("FROM official_warehouse_asn_shipping_batch_link link")
                .contains("COUNT(DISTINCT bridge.in_transit_batch_id) AS bridgeLogisticsCount")
                .contains("COUNT(DISTINCT bridge.purchase_order_id) AS bridgePurchaseOrderCount")
                .contains("CONVERT(")
                .contains("utf8mb4_unicode_ci")
                .doesNotContain("<>");
    }

    @Test
    void skuDetailReaderReturnsSoldProfitBatchesWithoutWritingOfficialFeeOrAsnFacts() throws Exception {
        Method method = PostSaleProfitBatchAttributionMapper.class.getMethod(
                "listProfitBatchLines",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                String.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM post_sale_profit_batch batch")
                .contains("JOIN latest_run ON latest_run.id = batch.run_id")
                .contains("batch.partner_sku COLLATE utf8mb4_unicode_ci = #{partnerSku} COLLATE utf8mb4_unicode_ci")
                .contains("ORDER BY batch.purchase_batch_time ASC, batch.id ASC")
                .doesNotContain("INSERT")
                .doesNotContain("UPDATE official_warehouse")
                .doesNotContain("official_commission")
                .doesNotContain("official_outbound_fee");
    }

    @Test
    void candidateBatchReaderBuildsReadOnlyPurchaseLogisticsAsnEvidencePool() throws Exception {
        Method method = PostSaleProfitBatchAttributionMapper.class.getMethod(
                "listCandidateBatchLines",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                String.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM procurement_ali1688_sku_purchase_batch batch")
                .contains("LEFT JOIN procurement_ali1688_sku_purchase_batch_source source")
                .contains("MANUAL_SKU_PURCHASE_BATCH")
                .contains("batch.counted_cost AS purchaseCostCny")
                .contains("existing_profit.purchase_source_id = CONCAT('MANUAL_SKU_PURCHASE_BATCH:', batch.id)")
                .contains("store_scope.project_code")
                .contains("FROM procurement_ali1688_sku_purchase_batch existing_batch")
                .contains("FROM procurement_purchase_order_item_site site")
                .contains("JOIN procurement_purchase_order_item item")
                .contains("JOIN procurement_purchase_order po")
                .contains("LEFT JOIN procurement_logistics_shipment_allocation allocation")
                .contains("LEFT JOIN in_transit_goods_line line")
                .contains("LEFT JOIN in_transit_batch in_batch")
                .contains("LEFT JOIN official_warehouse_asn_shipping_batch_link link")
                .contains("LEFT JOIN official_warehouse_asn asn")
                .contains("site.quantity AS purchaseQuantity")
                .contains("NULL AS purchaseCostCny")
                .contains("ORDER BY purchaseBatchTime ASC, sourceId ASC")
                .doesNotContain("INSERT")
                .doesNotContain("UPDATE")
                .doesNotContain("official_commission")
                .doesNotContain("official_outbound_fee")
                .doesNotContain("<>");
    }

    private static String selectSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }
}
