package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductSiteOfferLogisticsHistorySchemaTest {

    @Test
    void migrationAddsStoreProductLogisticsHistoryToProductSiteOffer() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/init/172_product_site_offer_logistics_history.sql"
        ));
        String bootstrap = Files.readString(Path.of(
                "src/main/java/com/nuono/next/system/LocalDbBootstrapStatusService.java"
        ));

        assertThat(bootstrap).contains("classpath:db/init/172_product_site_offer_logistics_history.sql");
        assertThat(migration)
                .contains("TABLE_NAME = 'product_site_offer'")
                .contains("COLUMN_NAME = 'logistics_has_history'")
                .contains("COLUMN_NAME = 'logistics_first_flow_at'")
                .contains("COLUMN_NAME = 'logistics_last_flow_at'")
                .contains("COLUMN_NAME = 'logistics_history_source'")
                .contains("CHARACTER_MAXIMUM_LENGTH")
                .contains("ADD COLUMN `logistics_has_history` BIT(1) NOT NULL DEFAULT b''0''")
                .contains("ADD COLUMN `logistics_first_flow_at` DATETIME DEFAULT NULL")
                .contains("ADD COLUMN `logistics_last_flow_at` DATETIME DEFAULT NULL")
                .contains("ADD COLUMN `logistics_history_source` VARCHAR(255) DEFAULT NULL")
                .contains("MODIFY COLUMN `logistics_history_source` VARCHAR(255) DEFAULT NULL")
                .contains("INDEX_NAME = 'idx_product_site_offer_logistics_history'")
                .contains("ADD KEY `idx_product_site_offer_logistics_history` (`logical_store_id`, `partner_sku`, `logistics_has_history`)")
                .contains("FROM `in_transit_goods_line` line")
                .contains("JOIN `in_transit_batch` batch")
                .contains("raw_owner_in_transit")
                .contains("line.sku")
                .contains("owner_unique_product")
                .contains("COUNT(DISTINCT owner_product.logical_store_id) = 1")
                .contains("owner_unique_product.partner_sku_key = raw_owner_in_transit.partner_sku_key")
                .contains("matched_history.product_site_offer_id")
                .contains("history.product_site_offer_id = pso.id")
                .contains("pso_match.id AS product_site_offer_id")
                .contains("FROM `official_warehouse_asn_line` asn_line")
                .contains("JOIN `official_warehouse_asn` asn")
                .contains("FROM `noon_order_line_fact` order_line")
                .contains("FROM `product_site_offer` stock_offer")
                .contains("COALESCE(stock_offer.fbn_stock, 0) + COALESCE(stock_offer.supermall_stock, 0) + COALESCE(stock_offer.fbp_stock, 0) > 0")
                .contains("'PRODUCT_SITE_OFFER_STOCK' AS source_code")
                .contains("FROM `official_warehouse_inventory_snapshot_line` inventory")
                .contains("inventory.is_current = b'1'")
                .contains("COALESCE(inventory.qty, 0) > 0")
                .contains("'OFFICIAL_WAREHOUSE_INVENTORY' AS source_code")
                .contains("GROUP BY source.logical_store_id, source.partner_sku_key")
                .contains("REGEXP '[0-9]-[0-9]+$'")
                .contains("REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '')")
                .doesNotContain("procurement_fulfillment_balance")
                .doesNotContain("PROCUREMENT_DISPATCH_BALANCE_BACKFILL")
                .doesNotContain("WAREHOUSE_DISPATCH_HANDOFF")
                .doesNotContain("warehouse_shipping")
                .doesNotContain("TRIM(balance.site_code)");
    }
}
