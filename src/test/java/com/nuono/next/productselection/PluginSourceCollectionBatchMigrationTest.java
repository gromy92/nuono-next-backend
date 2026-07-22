package com.nuono.next.productselection;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PluginSourceCollectionBatchMigrationTest {

    @Test
    void migrationPersistsBatchItemAndExtractorProvenanceWithRetryUniqueness() throws Exception {
        Path migration = Path.of(
                "src/main/resources/db/init/198_product_selection_plugin_collection_batch.sql"
        );

        String sql = Files.readString(migration);
        assertThat(sql)
                .contains("plugin_batch_id")
                .contains("plugin_item_key")
                .contains("extractor_version")
                .contains("uk_source_collection_plugin_batch_item")
                .contains("owner_user_id")
                .contains("logical_store_id")
                .contains("INFORMATION_SCHEMA.COLUMNS")
                .contains("INFORMATION_SCHEMA.STATISTICS");
    }
}
