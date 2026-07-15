package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductManagementMapperPublishTaskSqlTest {

    @Test
    void productSpecOverviewShouldNotUseFixedFiveHundredRowLimit() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectProductVariantSpecOverview".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(!sql.contains("LIMIT 500"));
    }

    @Test
    void updatePublishTaskStatusShouldRequireClaimFence() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "updateProductPublishTaskStatus".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ");

        assertTrue(sql.contains("status = #{expectedStatus}"));
        assertTrue(sql.contains("locked_by = #{expectedLockedBy}"));
        assertTrue(sql.contains("version_no = #{expectedVersionNo}"));
    }

    @Test
    void updatePublishTaskStatusShouldSetStatusOnlyOnce() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "updateProductPublishTaskStatus".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ");
        String marker = "SET status = #{status},";

        assertTrue(sql.contains(marker));
        assertTrue(
                sql.indexOf(marker) == sql.lastIndexOf(marker),
                "updateProductPublishTaskStatus must not emit duplicate SET status clauses"
        );
    }

    @Test
    void publishTaskQueriesShouldTreatWriteRetryScheduledAsActiveAndRunnable() {
        Method activeMethod = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectActiveProductPublishTask".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Method runnableMethod = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectRunnableProductPublishTasks".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String activeSql = String.join(" ", activeMethod.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
        String runnableSql = String.join(" ", runnableMethod.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertTrue(activeSql.contains("'write_retry_scheduled'"));
        assertTrue(runnableSql.contains("'write_retry_scheduled'"));
        assertTrue(activeSql.contains("'product_delete_queued'"));
        assertTrue(runnableSql.contains("'product_delete_queued'"));
        assertTrue(activeSql.contains("'product_delete_pending_effective'"));
        assertTrue(runnableSql.contains("'product_delete_pending_effective'"));
        assertTrue(activeSql.contains("'product_delete_write_retry_scheduled'"));
        assertTrue(runnableSql.contains("'product_delete_write_retry_scheduled'"));
        assertTrue(activeSql.contains("id = ( SELECT MAX(latest.id)"));
        assertTrue(runnableSql.contains("id = ( SELECT MAX(latest.id)"));
        assertTrue(runnableSql.contains("latest.product_master_id = product_publish_task.product_master_id"));
    }

    @Test
    void publishTaskRecordQueriesShouldUseExplicitResultMapForUnderscoreColumns() {
        for (String methodName : Arrays.asList(
                "selectProductPublishTaskByIdempotency",
                "selectActiveProductPublishTask",
                "selectRecentProductPublishTasks",
                "selectRunnableProductPublishTasks"
        )) {
            Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                    .filter((candidate) -> methodName.equals(candidate.getName()))
                    .findFirst()
                    .orElseThrow();
            ResultMap resultMap = method.getAnnotation(ResultMap.class);
            assertTrue(resultMap != null, methodName + " must map task_type/request_json/draft_json fields explicitly");
            assertTrue(
                    Arrays.asList(resultMap.value()).contains("ProductPublishTaskRecordMap"),
                    methodName + " must reuse ProductPublishTaskRecordMap"
            );
        }
    }

    @Test
    void staleRunningRecoveryShouldOnlyTouchLatestTaskPerProduct() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "recoverStaleRunningProductPublishTasks".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ");

        assertTrue(sql.contains("UPDATE product_publish_task t"));
        assertTrue(sql.contains("JOIN ( SELECT product_master_id, MAX(id) AS latest_id FROM product_publish_task"));
        assertTrue(sql.contains("latest.latest_id = t.id"));
        assertTrue(sql.contains("WHEN t.task_type = 'product-delete' THEN 'product_delete_verify_timeout'"));
        assertTrue(sql.contains("ELSE 'write_unknown'"));
        assertTrue(sql.contains("'product_delete_running'"));
        assertTrue(sql.contains("'product_delete_submitted'"));
        assertTrue(sql.contains("'product_delete_verifying'"));
    }

    @Test
    void updatePublishTaskStatusShouldIncrementRetryCountWhenWriteRetryIsScheduled() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "updateProductPublishTaskStatus".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ");

        assertTrue(sql.contains("retry_count = CASE"));
        assertTrue(sql.contains("WHEN #{status} IN ('write_retry_scheduled', 'product_delete_write_retry_scheduled') THEN COALESCE(retry_count, 0) + 1"));
    }

    @Test
    void refreshRetryScheduledTaskDraftShouldOnlyTouchUnlockedBackgroundRetry() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "refreshRetryScheduledProductPublishTaskDraft".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ");

        assertTrue(sql.contains("draft_json = #{draftJson}"));
        assertTrue(sql.contains("request_json = #{requestJson}"));
        assertTrue(sql.contains("changed_domains_json = #{changedDomainsJson}"));
        assertTrue(sql.contains("retry_count = 0"));
        assertTrue(sql.contains("next_run_at = NOW()"));
        assertTrue(sql.contains("status = 'write_retry_scheduled'"));
        assertTrue(sql.contains("locked_at IS NULL"));
        assertTrue(sql.contains("version_no = #{expectedVersionNo}"));
    }

    @Test
    void retryProductPublishTaskShouldPreserveProductDeleteQueueStatus() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "retryProductPublishTask".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ");

        assertTrue(sql.contains("status = CASE"));
        assertTrue(sql.contains("WHEN task_type = 'product-delete' THEN 'product_delete_queued'"));
        assertTrue(sql.contains("ELSE 'queued'"));
    }

    @Test
    void cancelQueuedProductPublishTaskShouldAllowProductDeleteQueueStatus() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "cancelQueuedProductPublishTask".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ");

        assertTrue(sql.contains("status IN ('queued', 'product_delete_queued')"));
    }

    @Test
    void legacyRetryableNoonWriteFailuresShouldRecoverOnlyLatestPerProduct() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "recoverRetryableFailedNoonWriteProductPublishTasks".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replace("&lt;", "<").replace("&gt;", ">").replaceAll("\\s+", " ");

        assertTrue(sql.contains("MAX(candidate.id) AS id"));
        assertTrue(sql.contains("candidate.status = 'failed'"));
        assertTrue(sql.contains("candidate.error_code IN ('noon_write_failed', 'publish_task_failed', 'noon_request_failed')"));
        assertTrue(sql.contains("candidate.error_message REGEXP 'HTTP[[:space:]]+(408|429|500|502|503|504)'"));
        assertTrue(sql.contains("LOWER(candidate.error_message) LIKE '%http 403%'"));
        assertTrue(sql.contains("LOWER(candidate.error_message) LIKE '%access denied%'"));
        assertTrue(sql.contains("LOWER(candidate.error_message) LIKE '%you don''t have permission to access%'"));
        assertTrue(sql.contains("COALESCE(candidate.retry_count, 0) < COALESCE(candidate.max_retry_count, 3)"));
        assertTrue(sql.contains("candidate.finished_at, candidate.gmt_updated, candidate.gmt_create"));
        assertTrue(sql.contains("DATE_SUB(NOW(), INTERVAL #{lookbackHours} HOUR)"));
        assertTrue(sql.contains("candidate.id = ( SELECT MAX(latest.id)"));
        assertTrue(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("'product_delete_pending_effective'"));
        assertTrue(sql.contains("'product_delete_write_retry_scheduled'"));
        assertTrue(sql.contains("WHEN t.task_type = 'product-delete' THEN 'product_delete_write_retry_scheduled'"));
        assertTrue(sql.contains("ELSE 'write_retry_scheduled'"));
        assertTrue(sql.contains("WHEN t.error_code IN ('publish_task_failed', 'noon_request_failed') THEN 'noon_request_failed'"));
        assertTrue(sql.contains("t.active_lock_key = CONCAT('product:', t.product_master_id)"));
    }

    @Test
    void listingStartedInitialBackfillShouldRevisitMissingOrNotListedOffersAndUseSiteCoverageForMissingFacts() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "backfillProductSiteOfferListingStartedAtById".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("pso.listing_started_at IS NULL"));
        assertTrue(sql.contains("pso.listing_started_source IS NULL OR pso.listing_started_source IN ('data_missing', 'not_listed')"));
        assertTrue(sql.contains("pm.logical_store_id = ls.id"));
        assertTrue(sql.contains("NOT EXISTS ( SELECT 1 FROM daily_sales_fact dsf"));
        assertTrue(sql.contains("WHEN NOT EXISTS ( SELECT 1 FROM daily_sales_fact dsf"));
        assertTrue(!sql.contains("COUNT(1) AS site_fact_row_count FROM daily_sales_fact dsf GROUP BY"));
        assertTrue(!sql.contains("NULLIF(pso.offer_code, '')"));
        assertTrue(!sql.contains("NULLIF(pso.psku_code, '')"));
        assertTrue(!sql.contains("NULLIF(pv.child_sku, '')"));
        assertTrue(!sql.contains("NULLIF(pm.sku_parent, '')"));
        assertTrue(sql.contains("ELSE 'not_listed'"));
    }

    @Test
    void productSiteOfferProjectionRowsShouldExposeListingStartedMetadataForRebuildInheritance() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectProductSiteOfferProjectionRows".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("DATE_FORMAT(pso.listing_started_at, '%Y-%m-%d %H:%i:%s') AS listingStartedAt"));
        assertTrue(sql.contains("pso.listing_started_source AS listingStartedSource"));
    }

    @Test
    void productListProjectionShouldExposeReadOnlyMaintenanceBoundaryAndFilterDisabledSites() {
        for (String methodName : Arrays.asList(
                "selectProductListProjection",
                "selectProductListProjectionByProductMasterId",
                "selectProductGroupCandidates"
        )) {
            Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                    .filter((candidate) -> methodName.equals(candidate.getName()))
                    .findFirst()
                    .orElseThrow();
            Select select = method.getAnnotation(Select.class);
            String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

            assertTrue(sql.contains("currentSiteMaintenanceEnabledFlag"), methodName);
            assertTrue(sql.contains("COALESCE(pso.maintenance_enabled, b'1') = b'1'"), methodName);
            assertTrue(sql.contains("COALESCE(anchor.site_enabled, b'1') = b'1'"), methodName);
            assertTrue(sql.contains("pso.id IS NULL OR COALESCE(lss.site_enabled, b'1') = b'1'"), methodName);
        }
    }

    @Test
    void productSiteOfferProjectionRowsShouldExposeMaintenanceBoundaryAndFilterDisabledSites() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectProductSiteOfferProjectionRows".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("maintenanceEnabledFlag"));
        assertTrue(sql.contains("COALESCE(pso.maintenance_enabled, b'1') = b'1'"));
        assertTrue(sql.contains("COALESCE(lss.site_enabled, b'1') = b'1'"));
    }

    @Test
    void productMaintenanceBoundaryMigrationShouldBeAdditiveAndDefaultEnabled() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/183_product_maintenance_boundary.sql"))
                .replaceAll("\\s+", " ");
        String baseSchema = Files.readString(Path.of("src/main/resources/db/init/003_product_management_v1.sql"))
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("ALTER TABLE `logical_store_site` ADD COLUMN `site_enabled` BIT(1) NOT NULL DEFAULT b''1''"));
        assertTrue(sql.contains("ALTER TABLE `product_site_offer` ADD COLUMN `maintenance_enabled` BIT(1) NOT NULL DEFAULT b''1''"));
        assertTrue(sql.contains("idx_logical_store_site_enabled"));
        assertTrue(sql.contains("idx_product_site_offer_maintenance"));
        assertTrue(!sql.toLowerCase().contains("drop table"));
        assertTrue(!sql.toLowerCase().contains("drop column"));
        assertTrue(baseSchema.contains("`site_enabled` BIT(1) NOT NULL DEFAULT b'1'"));
        assertTrue(baseSchema.contains("`maintenance_enabled` BIT(1) NOT NULL DEFAULT b'1'"));
    }

    @Test
    void listingStartedSalesFactRefreshShouldRevisitMissingOrNotListedOffers() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "refreshProductSiteOfferListingStartedAtBySalesFact".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("pso.listing_started_at IS NULL"));
        assertTrue(sql.contains("pso.listing_started_source IS NULL OR pso.listing_started_source IN ('data_missing', 'not_listed')"));
        assertTrue(sql.contains("pm.logical_store_id = ls.id"));
        assertTrue(sql.contains("site_fact_signal"));
        assertTrue(sql.contains("WHEN COALESCE(site_fact_signal.site_fact_row_count, 0) = 0 THEN 'data_missing'"));
        assertTrue(!sql.contains("NULLIF(pso.offer_code, '')"));
        assertTrue(!sql.contains("NULLIF(pso.psku_code, '')"));
        assertTrue(!sql.contains("NULLIF(pv.child_sku, '')"));
        assertTrue(!sql.contains("NULLIF(pm.sku_parent, '')"));
        assertTrue(sql.contains("ELSE 'not_listed'"));
    }

    @Test
    void emptySalesReportShouldMarkMissingSiteOffersAsNotListedOnly() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "markSiteProductOffersNotListedForEmptySalesReport".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("pso.listing_started_source = 'not_listed'"));
        assertTrue(sql.contains("pso.listing_started_at IS NULL"));
        assertTrue(sql.contains("pso.listing_started_source IS NULL OR pso.listing_started_source IN ('data_missing', 'not_listed')"));
        assertTrue(sql.contains("ls.owner_user_id = #{ownerUserId}"));
        assertTrue(sql.contains("lss.store_code = #{storeCode}"));
        assertTrue(sql.contains("lss.site = #{siteCode}"));
    }

    @Test
    void migration079ShouldRequireExplicitCoverageScopeAndAvoidGlobalSiteFactReclassification() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/079_product_site_offer_data_missing_site_coverage.sql"))
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("product_site_offer_listing_coverage_scope"));
        assertTrue(sql.contains("JOIN product_site_offer_listing_coverage_scope scope"));
        assertTrue(!sql.contains("FROM daily_sales_fact GROUP BY owner_user_id, store_code, site_code"));
        assertTrue(!sql.contains("site_fact_row_count"));
        assertTrue(!sql.contains("NULLIF(pso.offer_code, '')"));
        assertTrue(!sql.contains("NULLIF(pso.psku_code, '')"));
        assertTrue(!sql.contains("NULLIF(pv.child_sku, '')"));
        assertTrue(!sql.contains("NULLIF(pm.sku_parent, '')"));
        assertTrue(sql.contains("SELECT MIN(dsf.fact_date)"));
    }

    @Test
    void migration074ShouldUsePartnerSkuOnlyForListingStartedFactMatching() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/074_product_site_offer_listing_started_at.sql"))
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("NULLIF(pv.partner_sku, '')"));
        assertTrue(!sql.contains("NULLIF(pso.offer_code, '')"));
        assertTrue(!sql.contains("NULLIF(pso.psku_code, '')"));
        assertTrue(!sql.contains("NULLIF(pv.child_sku, '')"));
        assertTrue(!sql.contains("NULLIF(pm.sku_parent, '')"));
    }
}
