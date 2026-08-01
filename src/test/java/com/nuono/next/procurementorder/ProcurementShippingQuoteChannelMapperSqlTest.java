package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProcurementShippingQuoteChannelMapperSqlTest {

    @Test
    void shippingOrderQuoteLockTargetsTheExactChannel() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "selectLogisticsQuoteLineByShippingOrderChannelForUpdate",
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("shipping_order_id = #{shippingOrderId}");
        assertThat(sql).contains("purchase_order_item_site_id = #{itemSiteId}");
        assertThat(sql).contains("UPPER(COALESCE(forwarder_code, '')) = UPPER(COALESCE(#{forwarderCode}, ''))");
        assertThat(sql).contains("UPPER(COALESCE(route_code, '')) = UPPER(COALESCE(#{routeCode}, ''))");
        assertThat(sql).contains("UPPER(COALESCE(service_code, '')) = UPPER(COALESCE(#{serviceCode}, ''))");
        assertThat(sql).contains("FOR UPDATE");
    }

    @Test
    void channelIsolationMigrationKeysWarehouseQuotesByItemAndChannel() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/init/202_warehouse_logistics_quote_channel_isolation.sql"
        ));

        assertThat(migration).contains("active_item_site_slot");
        assertThat(migration).contains("forwarder_code");
        assertThat(migration).contains("route_code");
        assertThat(migration).contains("service_code");
        assertThat(migration).contains("UPPER(TRIM(");
        assertThat(migration).contains("FROM `product_forwarder_channel_quote` pfcq");
        assertThat(migration).contains("pfcq.`source_shipping_order_line_id`");
        assertThat(migration).contains("AND NOT EXISTS (");
        assertThat(migration).contains("UPDATE `product_management_id_sequence`");
    }

    @Test
    void segmentStateRequiresQuotesFromTheSelectedChannel() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "refreshShippingOrderSegmentState",
                Long.class,
                java.util.List.class,
                ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("NOT EXISTS (");
        assertThat(sql).contains("quote.unit_price > 0");
        assertThat(sql).doesNotContain("quote.quote_status = 'CONFIRMED'");
        assertThat(sql).contains(
                "UPPER(COALESCE(quote.forwarder_code, '')) = UPPER(COALESCE(#{row.forwarderCode}, ''))"
        );
        assertThat(sql).contains(
                "UPPER(COALESCE(quote.route_code, '')) = UPPER(COALESCE(#{row.routeCode}, ''))"
        );
        assertThat(sql).contains(
                "UPPER(COALESCE(quote.service_code, '')) = UPPER(COALESCE(#{row.serviceCode}, ''))"
        );
        assertThat(sql).doesNotContain("PARTIAL_SUBMITTED");
    }

    @Test
    void usableQuoteLookupUsesStrictPositivePriceInsteadOfCompatibilityStatus() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listUsableLogisticsQuoteLinesByShippingOrder", Long.class);

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("unit_price > 0");
        assertThat(sql).doesNotContain("AND quote_status");
        assertThat(sql).doesNotContain("forwarder_code, '')) = 'ZD'");
    }

    @Test
    void channelSnapshotLookupIncludesSubmittedMissingPricesForHistoricalFreeze() throws Exception {
        Method method = ProcurementPurchaseOrderMapper.class.getMethod(
                "listLogisticsQuoteChannelSnapshotsByShippingOrder", Long.class);

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("shipping_order_id = #{shippingOrderId}");
        assertThat(sql).contains("is_deleted = b'0'");
        assertThat(sql).doesNotContain("unit_price > 0");
        assertThat(sql).doesNotContain("AND quote_status");
    }

    @Test
    void snapshotRefreshAndAvailabilityProjectionBothFailClosedAfterSubmission() throws Exception {
        Method refresh = ProcurementPurchaseOrderMapper.class.getMethod(
                "refreshLogisticsQuoteLineSnapshot",
                ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord.class,
                Long.class
        );
        Method confirm = ProcurementPurchaseOrderMapper.class.getMethod(
                "confirmLogisticsQuoteLine",
                ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord.class,
                Long.class
        );

        String refreshSql = String.join(" ", refresh.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");
        String confirmSql = String.join(" ", confirm.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");

        assertThat(refreshSql).contains("shipping_submit_status = 'NOT_SUBMITTED'");
        assertThat(confirmSql).contains("CASE WHEN #{row.unitPrice} > 0 THEN 'CONFIRMED' ELSE 'PENDING_QUOTE' END");
        assertThat(confirmSql).contains("shipping_submit_status = 'NOT_SUBMITTED'");
    }
}
