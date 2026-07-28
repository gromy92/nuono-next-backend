package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WarehousePackingSoftDeleteReleaseMigrationTest {

    @Test
    void releaseCatalogAddsExactPackingListSoftDeleteIndex() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/init/233_warehouse_packing_soft_delete_index.sql"
        ));
        String postcheck = Files.readString(Path.of(
                "src/main/resources/db/postcheck/233_warehouse_packing_soft_delete_index.sql"
        ));
        String catalog = Files.readString(Path.of(
                "src/main/resources/db/init/release-migrations.tsv"
        ));

        assertThat(catalog).contains(
                "233\t233_warehouse_packing_soft_delete_index.sql\tAUTO_ADDITIVE"
        );
        assertThat(migration)
                .contains("warehouse_packing_box_item")
                .contains("idx_packing_box_item_list")
                .contains("ADD KEY `idx_packing_box_item_list` (`packing_list_id`, `is_deleted`)")
                .contains("1:packing_list_id,2:is_deleted")
                .contains("conflicting_index_count")
                .doesNotContain("DELETE FROM");
        assertThat(postcheck)
                .contains("idx_packing_box_item_list")
                .contains("1:packing_list_id,2:is_deleted")
                .contains("is_visible = 'YES'")
                .contains("expression IS NULL");
    }
}
