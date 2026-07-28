package com.nuono.next.product;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductBarcodeStoreUniquenessMigrationTest {

    private static final String LOCATION =
            "classpath:db/init/206_product_barcode_store_uniqueness.sql";

    @Test
    void migrationReplacesGlobalBarcodeUniquenessWithStoreBarcodeUniquenessIdempotently() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/init/206_product_barcode_store_uniqueness.sql"
        ));

        assertThat(sql)
                .contains("SET pb.logical_store_id = pv.logical_store_id")
                .contains("INFORMATION_SCHEMA.STATISTICS")
                .contains("INDEX_NAME = 'uk_product_barcode_barcode'")
                .contains("MODIFY COLUMN `logical_store_id` BIGINT NOT NULL")
                .contains("DROP INDEX `uk_product_barcode_barcode`")
                .contains("INDEX_NAME = 'uk_product_barcode_store_barcode'")
                .contains("ADD UNIQUE KEY `uk_product_barcode_store_barcode` (`logical_store_id`, `barcode`)")
                .doesNotContain("SIGNAL SQLSTATE");
        assertThat(sql.indexOf("MODIFY COLUMN `logical_store_id` BIGINT NOT NULL"))
                .isLessThan(sql.indexOf("DROP INDEX `uk_product_barcode_barcode`"));
        assertThat(sql.indexOf("DROP INDEX `uk_product_barcode_barcode`"))
                .isLessThan(sql.indexOf("ADD UNIQUE KEY `uk_product_barcode_store_barcode`"));
        assertThat(sql.indexOf("SET pb.logical_store_id = pv.logical_store_id"))
                .isLessThan(sql.indexOf("MODIFY COLUMN `logical_store_id` BIGINT NOT NULL"));
        assertThat(sql).containsOnlyOnce("ALTER TABLE `product_barcode`");
        assertInitScriptsInclude(LOCATION);
    }
}
