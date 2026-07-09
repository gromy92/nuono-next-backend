package com.nuono.next.postsaleprofit;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostSaleProfitSchemaContractTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "179_post_sale_profit_center.sql"
    );
    private static final Path LOGISTICS_CLOSURE_MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "180_procurement_logistics_shipment_allocation.sql"
    );

    @Test
    void migrationDefinesPostSaleProfitRunBatchAttributionAndFxTables() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `post_sale_profit_recalculation_run`")
                .contains("CREATE TABLE IF NOT EXISTS `post_sale_profit_batch`")
                .contains("CREATE TABLE IF NOT EXISTS `post_sale_profit_order_attribution`")
                .contains("CREATE TABLE IF NOT EXISTS `post_sale_profit_fx_rate`")
                .contains("`purchase_unit_cost_cny` DECIMAL(18,6) DEFAULT NULL")
                .contains("`headhaul_unit_cost_cny` DECIMAL(18,6) DEFAULT NULL")
                .contains("`profit_cny` DECIMAL(18,6) DEFAULT NULL")
                .contains("`quality_status_json` JSON NOT NULL")
                .contains("`evidence_json` JSON DEFAULT NULL")
                .contains("KEY `idx_psp_batch_sku_time`")
                .contains("KEY `idx_psp_attr_item`")
                .doesNotContain("official_commission")
                .doesNotContain("official_outbound_fee");
    }

    @Test
    void localDbBootstrapListsPostSaleProfitMigration() throws Exception {
        assertInitScriptsInclude(
                "classpath:db/init/179_post_sale_profit_center.sql",
                "classpath:db/init/180_procurement_logistics_shipment_allocation.sql",
                "classpath:db/init/181_post_sale_profit_menu_permission.sql"
        );
    }

    @Test
    void migrationDefinesProcurementLogisticsShipmentAllocationTable() throws Exception {
        String sql = Files.readString(LOGISTICS_CLOSURE_MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `procurement_logistics_shipment_allocation`")
                .contains("`confirmation_status` VARCHAR(30) NOT NULL")
                .contains("`match_method` VARCHAR(60) NOT NULL")
                .contains("`in_transit_goods_line_id` BIGINT NOT NULL")
                .contains("KEY `idx_proc_logistics_alloc_source`")
                .contains("KEY `idx_proc_logistics_alloc_in_transit_line`")
                .contains("'procurement_logistics_shipment_allocation'");
    }
}
