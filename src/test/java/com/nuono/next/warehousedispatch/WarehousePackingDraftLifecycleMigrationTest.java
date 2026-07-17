package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WarehousePackingDraftLifecycleMigrationTest {

    @Test
    void migrationAllowsDraftSpecsAndScopesUniquenessToActiveRows() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/init/187_warehouse_packing_draft_lifecycle.sql"
        ));

        assertThat(migration)
                .contains("MODIFY COLUMN `length_cm` DECIMAL(12, 3) DEFAULT NULL")
                .contains("MODIFY COLUMN `gross_weight_kg` DECIMAL(12, 3) DEFAULT NULL")
                .contains("active_outbound_order_id")
                .contains("uk_warehouse_packing_active_outbound")
                .contains("active_box_no")
                .contains("uk_warehouse_packing_active_box_no")
                .contains("DROP INDEX `uk_warehouse_packing_box_no`")
                .contains("INFORMATION_SCHEMA.COLUMNS")
                .contains("INFORMATION_SCHEMA.STATISTICS");
    }
}
