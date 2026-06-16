package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductPublicDetailMigrationBoundaryTest {
    @Test
    void migrationCreatesOnlyPublicDetailSnapshotCapability() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/133_product_public_detail_snapshot.sql"));

        assertTrue(sql.contains("product_public_detail_snapshot"));
        assertFalse(sql.contains("product_master_snapshot"));
        assertFalse(sql.contains("product_image_profile"));
    }
}
