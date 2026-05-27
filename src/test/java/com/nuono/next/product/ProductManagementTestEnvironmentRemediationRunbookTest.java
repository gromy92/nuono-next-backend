package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductManagementTestEnvironmentRemediationRunbookTest {

    @Test
    void remediationRunbookNamesTheHumanOnlyEnvironmentStepsAndNoPublishBoundary() throws Exception {
        Path runbook = findWorkspaceRoot().resolve(
                ".scratch/product-management-current-requirements-status/test-env-product-management-remediation-runbook.md"
        );
        assertTrue(Files.exists(runbook), () -> "Missing test env remediation runbook: " + runbook);

        String content = Files.readString(runbook);
        assertContains(content, "http://123.60.15.70/ai/");
        assertContains(content, "xingyaoqw");
        assertContains(content, "boss123");
        assertContains(content, "020_restore_xingyao_product_management_credentials.sql");
        assertContains(content, "033_product_management_offer_list_official_metrics.sql");
        assertContains(content, "034_product_publish_task.sql");
        assertContains(content, "035_noon_attribute_dictionary.sql");
        assertContains(content, "036_noon_brand_fulltype_dictionary.sql");
        assertContains(content, "050_product_management_product_source_type.sql");
        assertContains(content, "product_master.product_source_type");
        assertContains(content, "product_site_offer.views_count");
        assertContains(content, "product_publish_task");
        assertContains(content, "noon_attribute_field");
        assertContains(content, "noon_brand_dictionary");
        assertContains(content, "publish-current");
        assertContains(content, "publish-current-site");
        assertContains(content, "Issue 20");
    }

    private static Path findWorkspaceRoot() throws IOException {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            if (Files.exists(cursor.resolve(".scratch/product-management-current-requirements-status/PRD.md"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Unable to locate workspace root from " + Path.of("").toAbsolutePath());
    }

    private static void assertContains(String content, String expected) {
        assertTrue(content.contains(expected), () -> "Expected runbook to contain: " + expected);
    }
}
