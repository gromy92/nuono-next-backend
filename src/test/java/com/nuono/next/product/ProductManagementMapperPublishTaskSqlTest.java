package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductManagementMapperPublishTaskSqlTest {

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
        assertTrue(sql.contains("site_fact_signal"));
        assertTrue(sql.contains("WHEN COALESCE(site_fact_signal.site_fact_row_count, 0) = 0 THEN 'data_missing'"));
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
}
