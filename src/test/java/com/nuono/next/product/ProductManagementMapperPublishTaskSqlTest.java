package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
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
        assertTrue(activeSql.contains("id = ( SELECT MAX(latest.id)"));
        assertTrue(runnableSql.contains("id = ( SELECT MAX(latest.id)"));
        assertTrue(runnableSql.contains("latest.product_master_id = product_publish_task.product_master_id"));
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
        assertTrue(sql.contains("t.status IN ('running', 'submitted', 'verifying')"));
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
        assertTrue(sql.contains("WHEN #{status} = 'write_retry_scheduled' THEN COALESCE(retry_count, 0) + 1"));
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
        assertTrue(sql.contains("active.status IN ('queued', 'running', 'submitted', 'verifying', 'pending_effective', 'write_unknown', 'verify_timeout', 'write_retry_scheduled')"));
        assertTrue(sql.contains("SET t.status = 'write_retry_scheduled'"));
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
        assertTrue(sql.contains("ELSE 'not_listed'"));
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
        assertTrue(sql.contains("SELECT MIN(dsf.fact_date)"));
    }
}
