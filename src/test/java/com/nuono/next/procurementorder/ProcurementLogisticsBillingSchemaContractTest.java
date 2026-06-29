package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProcurementLogisticsBillingSchemaContractTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "148_procurement_logistics_billing.sql"
    );

    @Test
    void migrationDefinesProductChannelQuotesExpectedBillsActualBillLinksAndReconciliation() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `product_forwarder_channel_quote`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `logistics_expected_bill`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `logistics_expected_bill_component`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `logistics_actual_bill_shipping_order_link`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `logistics_bill_reconciliation`");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `logistics_bill_reconciliation_item`");

        assertThat(sql).contains("`effective_status` VARCHAR(40) NOT NULL DEFAULT 'CURRENT'");
        assertThat(sql).contains("`active_quote_slot` VARCHAR(420)");
        assertThat(sql).contains("UNIQUE KEY `uk_product_forwarder_channel_quote_current` (`active_quote_slot`)");
        assertThat(sql).contains("`bill_status` VARCHAR(40) NOT NULL DEFAULT 'GENERATED'");
        assertThat(sql).contains("`reconciliation_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING_ACTUAL_BILL'");
        assertThat(sql).contains("SELECT 'product_forwarder_channel_quote'");
        assertThat(sql).contains("SELECT 'logistics_expected_bill'");
        assertThat(sql).contains("SELECT 'logistics_expected_bill_component'");
    }
}
