package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductLifecycleListingSourceSchemaContractTest {

    @Test
    void productSiteOfferCanStoreOfficialListingTimeForLifecycleResolution() throws IOException {
        String sql = Files.readString(Path.of(
                "src",
                "main",
                "resources",
                "db",
                "init",
                "057_product_lifecycle_listing_source.sql"
        ));

        assertTrue(sql.contains("ADD COLUMN `official_listing_at` DATETIME DEFAULT NULL"));
        assertTrue(sql.contains("KEY `idx_product_site_offer_official_listing`"));
    }
}
