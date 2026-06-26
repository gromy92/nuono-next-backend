package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProcurementAli1688HistoryReadModelSchemaTest {

    @Test
    void purchaseOrderAli1688HistorySchemasAreIncludedInLocalDbBootstrapList() throws Exception {
        String java = Files.readString(Path.of("src/main/java/com/nuono/next/system/LocalDbBootstrapStatusService.java"));

        assertThat(java)
                .contains("classpath:db/init/118_procurement_ali1688_order_sku_allocation.sql")
                .contains("classpath:db/init/119_procurement_purchase_order.sql")
                .contains("classpath:db/init/122_procurement_purchase_order_logistics_plan.sql")
                .contains("classpath:db/init/123_procurement_purchase_order_transport_mode.sql")
                .contains("classpath:db/init/141_procurement_ali1688_sku_purchase_batch_combo_support.sql");
    }

    @Test
    void existingAli1688SchemasProvideTablesUsedByPurchaseOrderHistoryEndpoint() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/071_procurement_ali1688_historical_order_sync.sql"))
                + Files.readString(Path.of("src/main/resources/db/init/073_procurement_ali1688_order_assignment.sql"))
                + Files.readString(Path.of("src/main/resources/db/init/094_procurement_ali1688_order_product_link.sql"))
                + Files.readString(Path.of("src/main/resources/db/init/096_procurement_ali1688_sku_purchase_batch.sql"))
                + Files.readString(Path.of("src/main/resources/db/init/118_procurement_ali1688_order_sku_allocation.sql"));

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_header`")
                .contains("CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_item`")
                .contains("CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_item_assignment`")
                .contains("CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_item_product_link`")
                .contains("CREATE TABLE IF NOT EXISTS `procurement_ali1688_order_sku_allocation`")
                .contains("CREATE TABLE IF NOT EXISTS `procurement_ali1688_sku_purchase_batch`")
                .contains("CREATE TABLE IF NOT EXISTS `procurement_ali1688_sku_purchase_batch_source`")
                .contains("`batch_type`")
                .contains("`counted_quantity_unit`")
                .contains("`component_count`")
                .contains("`expected_component_count`")
                .contains("`component_sequence`")
                .contains("`source_quantity_per_counted_unit`");
    }
}
