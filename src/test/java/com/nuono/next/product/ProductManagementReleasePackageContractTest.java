package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductManagementReleasePackageContractTest {

    @Test
    void testPackageBuildVerifiesProductManagementMigrationsBeforePublishing() throws Exception {
        String buildScript = Files.readString(Path.of("..", "release", "build-nuono-next-test-package.sh"));
        String verifyScript = Files.readString(Path.of("..", "release", "verify-product-management-test-package.sh"));

        assertTrue(buildScript.contains("verify-product-management-test-package.sh"));
        assertTrue(verifyScript.contains("020_restore_xingyao_product_management_credentials.sql"));
        assertTrue(verifyScript.contains("033_product_management_offer_list_official_metrics.sql"));
        assertTrue(verifyScript.contains("034_product_publish_task.sql"));
        assertTrue(verifyScript.contains("035_noon_attribute_dictionary.sql"));
        assertTrue(verifyScript.contains("036_noon_brand_fulltype_dictionary.sql"));
        assertTrue(verifyScript.contains("050_product_management_product_source_type.sql"));
        assertTrue(verifyScript.contains("jar tf"));
    }
}
