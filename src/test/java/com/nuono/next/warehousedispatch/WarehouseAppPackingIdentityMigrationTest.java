package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WarehouseAppPackingIdentityMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "184_warehouse_app_packing_identity_fields.sql"
    );

    @Test
    void migrationBackfillsLegacySealedBoxesAndStableProductIdentity() throws IOException {
        String sql = Files.readString(MIGRATION).replaceAll("\\s+", " ");

        assertThat(sql).contains("packing_list.status IN ('CONFIRMED', 'SHIPPED')");
        assertThat(sql).contains("SET box.status = 'SEALED'");
        assertThat(sql).contains("JOIN product_master product");
        assertThat(sql).contains("JOIN logical_store_site store_site");
        assertThat(sql).contains("UPPER(store_site.site) = UPPER(outbound_line.site_code)");
        assertThat(sql).contains("outbound_line.logical_store_id = COALESCE(outbound_line.logical_store_id, product.logical_store_id)");
        assertThat(sql).contains("outbound_line.source_store_code = COALESCE(outbound_line.source_store_code, store_site.store_code)");
        assertThat(sql).contains("JOIN warehouse_outbound_order_line outbound_line");
    }
}
