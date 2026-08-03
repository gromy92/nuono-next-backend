package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProductMasterPskuZcodeLookupMigrationTest {

    @Test
    void migrationReplacesLegacyZCodeUniquenessWithLookupIndex() throws Exception {
        String sql;
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "db/init/224_product_master_psku_zcode_lookup.sql"
        )) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("INFORMATION_SCHEMA.STATISTICS")
                .contains("NON_UNIQUE = 0")
                .contains("ADD INDEX `idx_product_master_store_sku_parent_lookup` "
                        + "(`logical_store_id`, `sku_parent`, `is_deleted`)")
                .contains("DROP INDEX `uk_product_master_store_sku_parent`");
        assertThat(sql.indexOf("ADD INDEX `idx_product_master_store_sku_parent_lookup`"))
                .isLessThan(sql.indexOf("DROP INDEX `uk_product_master_store_sku_parent`"));
    }
}
