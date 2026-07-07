package com.nuono.next.postsaleprofit.logisticsclosure;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.PostSaleProfitLogisticsClosureMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class PostSaleProfitLogisticsClosureMapperSqlTest {

    @Test
    void purchaseBatchReaderUsesAli1688PurchaseBatchesProfitSourcesAndConfirmedAllocationQuantities() throws Exception {
        Method method = PostSaleProfitLogisticsClosureMapper.class.getMethod(
                "listPurchaseBatches",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                String.class,
                String.class,
                Integer.class,
                Integer.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("latest_run AS")
                .contains("headhaul_candidate_lines AS")
                .contains("FROM post_sale_profit_batch profit")
                .contains("JOIN latest_run ON latest_run.id = profit.run_id")
                .contains("profit.purchase_source_type IN ('MANUAL_SKU_PURCHASE_BATCH', 'ALI1688_PRODUCT_LINK', 'ALI1688_ALLOCATION')")
                .contains("LEFT JOIN procurement_logistics_shipment_allocation allocation")
                .contains("allocation.confirmation_status = 'CONFIRMED'")
                .contains("run.date_from = #{dateFrom}")
                .contains("run.date_to = #{dateTo}")
                .contains("GREATEST(purchase.purchase_quantity - COALESCE(SUM(allocation.allocated_quantity), 0), 0) AS remaining_quantity")
                .contains("estimated_headhaul")
                .contains("candidate_line_count")
                .contains("headhaul_candidate_line_count")
                .contains("headhaul_gap_type")
                .contains("no_same_sku_in_transit_candidate")
                .contains("same_sku_in_transit_without_headhaul_bill")
                .contains("headhaul_available_unconfirmed");
    }

    @Test
    void summaryReaderSeparatesTrueHeadhaulGapsFromUnconfirmedAttribution() throws Exception {
        Method method = PostSaleProfitLogisticsClosureMapper.class.getMethod(
                "selectSummary",
                Long.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("missingInTransitCandidateBatchCount")
                .contains("missingHeadhaulBillBatchCount")
                .contains("headhaulAvailableUnconfirmedBatchCount");
    }

    @Test
    void candidateReaderUsesInTransitGoodsLinesHeadhaulComponentsAndRejectedFilter() throws Exception {
        Method method = PostSaleProfitLogisticsClosureMapper.class.getMethod(
                "listCandidates",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM in_transit_goods_line line")
                .contains("JOIN in_transit_batch batch")
                .contains("LEFT JOIN in_transit_freight_actual_component component")
                .contains("LEFT JOIN ( SELECT batch_headhaul.owner_user_id, batch_headhaul.batch_id")
                .contains("SUM(batch_headhaul.cny_amount) / NULLIF(SUM(line_total.shipped_quantity), 0) AS batch_headhaul_unit_cost_cny")
                .contains("COALESCE(SUM(component.cny_amount), MAX(batch_prorated.batch_headhaul_unit_cost_cny) * line.shipped_quantity) AS headhaul_cost_cny")
                .contains("forwarder.forwarder_name")
                .contains("component.standard_fee_type = 'HEADHAUL'")
                .contains("line.psku COLLATE utf8mb4_unicode_ci = #{partnerSku} COLLATE utf8mb4_unicode_ci")
                .contains("line.site_code COLLATE utf8mb4_unicode_ci = #{siteCode} COLLATE utf8mb4_unicode_ci")
                .contains("NOT EXISTS (")
                .contains("rejected.confirmation_status = 'REJECTED'");
    }

    @Test
    void allocationWriterPersistsExplicitConfirmedOrRejectedRelationship() throws Exception {
        Method method = PostSaleProfitLogisticsClosureMapper.class.getMethod(
                "insertAllocation",
                PostSaleProfitLogisticsClosureRecords.LogisticsClosureAllocationRow.class
        );

        String sql = insertSql(method);

        assertThat(sql)
                .contains("INSERT INTO procurement_logistics_shipment_allocation")
                .contains("source_type, source_id, source_line_id")
                .contains("in_transit_batch_id, in_transit_goods_line_id")
                .contains("allocated_quantity, allocation_unit, match_method, confirmation_status")
                .contains("#{row.confirmationStatus}")
                .contains("#{row.evidenceJson}");
    }

    @Test
    void confirmedHeadhaulReaderUsesConfirmedAllocationsBeforeProfitRecalculation() throws Exception {
        Method method = PostSaleProfitLogisticsClosureMapper.class.getMethod(
                "listConfirmedHeadhaulAllocations",
                Long.class,
                String.class,
                String.class
        );

        String sql = selectSql(method);

        assertThat(sql)
                .contains("FROM procurement_logistics_shipment_allocation allocation")
                .contains("JOIN in_transit_goods_line line")
                .contains("LEFT JOIN in_transit_freight_actual_component component")
                .contains("allocation.confirmation_status = 'CONFIRMED'")
                .contains("allocation.target_store_code = #{storeCode}")
                .contains("allocation.target_site_code = #{siteCode}")
                .contains("COALESCE(SUM(component.cny_amount) / NULLIF(SUM(component.quantity), 0), MAX(batch_prorated.batch_headhaul_unit_cost_cny)) AS headhaul_unit_cost_cny")
                .contains("confirmed_batch_headhaul_prorated_by_shipped_quantity");
    }

    private static String selectSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }

    private static String insertSql(Method method) {
        return String.join(" ", method.getAnnotation(Insert.class).value())
                .replaceAll("\\s+", " ");
    }
}
