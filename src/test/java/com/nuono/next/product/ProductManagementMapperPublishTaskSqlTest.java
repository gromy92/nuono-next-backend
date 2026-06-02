package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
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
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("status = #{expectedStatus}"));
        assertTrue(sql.contains("locked_by = #{expectedLockedBy}"));
        assertTrue(sql.contains("version_no = #{expectedVersionNo}"));
    }

    @Test
    void listingStartedInitialBackfillShouldOnlyRunForUncalculatedOffersAndMarkMissingFacts() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "backfillProductSiteOfferListingStartedAtById".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("pso.listing_started_at IS NULL"));
        assertTrue(sql.contains("pso.listing_started_source IS NULL"));
        assertTrue(sql.contains("'data_missing'"));
        assertTrue(sql.contains("COUNT(1)"));
    }

    @Test
    void listingStartedSalesFactRefreshShouldOnlyRunForUncalculatedOffers() {
        Method method = Arrays.stream(ProductManagementMapper.class.getDeclaredMethods())
                .filter((candidate) -> "refreshProductSiteOfferListingStartedAtBySalesFact".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("pso.listing_started_at IS NULL"));
        assertTrue(sql.contains("pso.listing_started_source IS NULL"));
        assertTrue(sql.contains("'data_missing'"));
    }
}
