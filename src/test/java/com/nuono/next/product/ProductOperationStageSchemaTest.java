package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductOperationStageSchemaTest {

    @Test
    void migrationAddsOperationStageColumnsToProductSiteOffer() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/init/176_product_operation_stage.sql"
        ));
        String bootstrap = Files.readString(Path.of(
                "src/main/java/com/nuono/next/system/LocalDbBootstrapStatusService.java"
        ));

        assertThat(bootstrap).contains("classpath:db/init/176_product_operation_stage.sql");
        assertThat(migration)
                .contains("TABLE_NAME = 'product_site_offer'")
                .contains("COLUMN_NAME = 'operation_stage_code'")
                .contains("COLUMN_NAME = 'operation_stage_updated_at'")
                .contains("COLUMN_NAME = 'operation_stage_updated_by'")
                .contains("ADD COLUMN `operation_stage_code` VARCHAR(32) DEFAULT NULL")
                .contains("ADD COLUMN `operation_stage_updated_at` DATETIME DEFAULT NULL")
                .contains("ADD COLUMN `operation_stage_updated_by` BIGINT DEFAULT NULL")
                .contains("INDEX_NAME = 'idx_product_site_offer_operation_stage'")
                .contains("ADD KEY `idx_product_site_offer_operation_stage` (`logical_store_id`, `site_id`, `operation_stage_code`)");
    }
}
