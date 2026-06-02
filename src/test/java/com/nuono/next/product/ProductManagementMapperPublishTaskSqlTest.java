package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("status = #{expectedStatus}"));
        assertTrue(sql.contains("locked_by = #{expectedLockedBy}"));
        assertTrue(sql.contains("version_no = #{expectedVersionNo}"));
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
    void currentSiteProjectionFieldsShouldNotFallbackToOtherStoreOffers() {
        List<String> projectionMethods = List.of(
                "selectProductListProjection",
                "selectProductListProjectionBySkuParent",
                "selectProductGroupCandidates"
        );

        for (String methodName : projectionMethods) {
            Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                    .filter((candidate) -> methodName.equals(candidate.getName()))
                    .findFirst()
                    .orElseThrow();
            Select select = method.getAnnotation(Select.class);
            String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

            assertTrue(!sql.contains("MAX(pso.psku_code)"), methodName);
            assertTrue(!sql.contains("MAX(pso.offer_code)"), methodName);
            assertTrue(!sql.contains("MAX(COALESCE(pso.final_price, pso.sale_price, pso.price))"), methodName);
            assertTrue(!sql.contains("MAX(pso.price)"), methodName);
            assertTrue(!sql.contains("MAX(pso.sale_price)"), methodName);
            assertTrue(!sql.contains("MAX(pso.views_count)"), methodName);
            assertTrue(!sql.contains("MAX(pso.units_sold)"), methodName);
            assertTrue(!sql.contains("MAX(pso.sales_amount)"), methodName);
            assertTrue(!sql.contains("MAX(pso.sales_currency)"), methodName);
            assertTrue(!sql.contains("MAX(pso.live_status)"), methodName);
            assertTrue(!sql.contains("MAX(pso.status_code)"), methodName);
            assertTrue(!sql.contains("MAX(pso.listing_started_at)"), methodName);
            assertTrue(!sql.contains("MAX(pso.listing_started_source)"), methodName);
            assertTrue(!sql.contains("MAX(CASE WHEN pso.is_active = b'1' THEN 1 ELSE 0 END)"), methodName);
        }
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
