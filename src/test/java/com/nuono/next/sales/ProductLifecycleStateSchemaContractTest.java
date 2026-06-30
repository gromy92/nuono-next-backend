package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductLifecycleStateSchemaContractTest {

    @Test
    void currentStateUsesPartnerSkuAsProductIdentity() throws IOException {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "init", "056_product_lifecycle_state_engine.sql"));

        assertTrue(sql.contains("UNIQUE KEY `uk_product_lifecycle_current_scope` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`)"));
        assertFalse(sql.contains("UNIQUE KEY `uk_product_lifecycle_current_scope` (`owner_user_id`, `store_code`, `site_code`, `partner_sku`, `sku`)"));
    }
}
