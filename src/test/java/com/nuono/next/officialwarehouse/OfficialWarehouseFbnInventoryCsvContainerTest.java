package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OfficialWarehouseFbnInventoryCsvContainerTest {

    private static final String HEADER =
            "warehouse_code,qty,inventory_type,partner_sku,inventory_snapshot_at\n";

    private final OfficialWarehouseFbnInventoryResponseParser parser =
            new OfficialWarehouseFbnInventoryResponseParser(new ObjectMapper());

    @Test
    void acceptsOneStructurallyClosedCsvDocumentWithOneSnapshotTimestamp() {
        OfficialWarehouseFbnInventoryProvider.InventoryPage page = parse(
                HEADER
                        + "RUH01,7,saleable,SKU-1,2026-08-02 23:00:00\n"
                        + "RUH01,2,damaged,SKU-2,2026-08-02 23:00:00\n"
        );

        assertThat(page.completeExport).isTrue();
        assertThat(page.items).hasSize(2);
        assertThat(page.items)
                .extracting(item -> item.inventorySnapshotAt)
                .containsOnly("2026-08-02 23:00:00");
        assertThat(page.providerGenerationToken).isNull();
        assertThat(page.providerExportToken).isNull();
        assertThat(page.declaredCollectionCount).isNull();
    }

    @Test
    void rejectsHeaderOnlyCsvBecauseItCannotProveAnEmptySnapshotExtent() {
        assertThatThrownBy(() -> parse(HEADER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty snapshot extent is unproven");
    }

    @Test
    void rejectsRowsWhoseWidthDoesNotMatchTheHeader() {
        assertThatThrownBy(() -> parse(
                HEADER + "RUH01,7,saleable,SKU-1\n"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row width is inconsistent");

        assertThatThrownBy(() -> parse(
                HEADER + "RUH01,7,saleable,SKU-1,2026-08-02 23:00:00,EXTRA\n"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row width is inconsistent");
    }

    @Test
    void rejectsDuplicateHeadersAndMissingSnapshotTimestampColumn() {
        assertThatThrownBy(() -> parse(
                "warehouse_code,qty,inventory_type,partner_sku,partner_sku\n"
                        + "RUH01,7,saleable,SKU-1,SKU-1\n"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header is invalid");

        assertThatThrownBy(() -> parse(
                "warehouse_code,qty,inventory_type,partner_sku\n"
                        + "RUH01,7,saleable,SKU-1\n"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columns are incomplete");
    }

    @Test
    void rejectsMissingOrMixedRowSnapshotTimestamps() {
        assertThatThrownBy(() -> parse(
                HEADER + "RUH01,7,saleable,SKU-1,\n"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot timestamp is missing");

        assertThatThrownBy(() -> parse(
                HEADER
                        + "RUH01,7,saleable,SKU-1,2026-08-02 23:00:00\n"
                        + "RUH01,2,damaged,SKU-2,2026-08-02 23:00:01\n"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot timestamp drift");
    }

    private OfficialWarehouseFbnInventoryProvider.InventoryPage parse(String csv) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8), 1);
    }
}
