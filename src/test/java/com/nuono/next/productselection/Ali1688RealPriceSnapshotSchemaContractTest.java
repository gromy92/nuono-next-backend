package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Ali1688RealPriceSnapshotSchemaContractTest {

    @Test
    void realPriceSnapshotMigrationCreatesSeparateAuditablePreviewTable() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "070_product_selection_ali1688_real_price_snapshot.sql"
        ));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `product_selection_ali1688_real_price_snapshot`"));
        assertTrue(sql.contains("`candidate_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`sku_text` VARCHAR(500) DEFAULT NULL"));
        assertTrue(sql.contains("`quantity` INT NOT NULL DEFAULT 1"));
        assertTrue(sql.contains("`unit_price` DECIMAL(18,4) DEFAULT NULL"));
        assertTrue(sql.contains("`freight_price` DECIMAL(18,4) DEFAULT NULL"));
        assertTrue(sql.contains("`discount_price` DECIMAL(18,4) DEFAULT NULL"));
        assertTrue(sql.contains("`total_price` DECIMAL(18,4) DEFAULT NULL"));
        assertTrue(sql.contains("`currency` VARCHAR(20) NOT NULL DEFAULT 'CNY'"));
        assertTrue(sql.contains("`rmb_total_price` DECIMAL(18,4) DEFAULT NULL"));
        assertTrue(sql.contains("`exchange_rate_to_rmb` DECIMAL(18,8) DEFAULT NULL"));
        assertTrue(sql.contains("`region_text` VARCHAR(200) DEFAULT NULL"));
        assertTrue(sql.contains("`address_context_json` TEXT DEFAULT NULL"));
        assertTrue(sql.contains("`source` VARCHAR(40) NOT NULL"));
        assertTrue(sql.contains("`safety_mode` VARCHAR(40) NOT NULL DEFAULT 'preview_only'"));
        assertTrue(sql.contains("`failure_code` VARCHAR(100) DEFAULT NULL"));
        assertTrue(sql.contains("`failure_message` VARCHAR(500) DEFAULT NULL"));
        assertTrue(sql.contains("`captured_at` DATETIME NOT NULL"));
        assertTrue(sql.contains("'product_selection_ali1688_real_price_snapshot'"));
        assertFalse(sql.contains("ALTER TABLE `product_selection_ali1688_candidate`"));
        assertFalse(sql.contains("ALTER TABLE product_selection_ali1688_candidate"));
    }
}
