package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductImageSuiteReadinessPolicyTest {

    @Test
    void requiresMainBothDetailRolesSizeAndScene() {
        List<ProductImageSuiteAssetRecord> assets = completedReviewAssets();

        assertTrue(ProductImageSuiteReadinessPolicy.isReadyForReview(assets));

        assets.removeIf(asset -> asset.getImageRole() == ProductImageSuiteAssetRole.USAGE_SCENE);
        assertFalse(ProductImageSuiteReadinessPolicy.isReadyForReview(assets));
    }

    @Test
    void packageImageDoesNotControlReviewReadiness() {
        List<ProductImageSuiteAssetRecord> assets = completedReviewAssets();

        assertTrue(assets.stream().noneMatch(
                asset -> asset.getImageRole() == ProductImageSuiteAssetRole.PACKAGE_LIST
        ));
        assertTrue(ProductImageSuiteReadinessPolicy.isReadyForReview(assets));
    }

    @Test
    void incompletePendingReviewIsPresentedAsGenerating() {
        List<ProductImageSuiteAssetRecord> assets = completedReviewAssets();
        assets.get(0).setSha256(null);

        assertEquals(
                ProductImageSuiteStatus.GENERATING,
                ProductImageSuiteReadinessPolicy.operatorFacingStatus(
                        ProductImageSuiteStatus.PENDING_REVIEW,
                        assets
                )
        );
    }

    private List<ProductImageSuiteAssetRecord> completedReviewAssets() {
        List<ProductImageSuiteAssetRecord> assets = new ArrayList<>();
        assets.add(asset(1L, ProductImageSuiteAssetRole.MAIN));
        assets.add(asset(2L, ProductImageSuiteAssetRole.SIZE));
        assets.add(asset(3L, ProductImageSuiteAssetRole.CORE_FEATURE));
        assets.add(asset(4L, ProductImageSuiteAssetRole.MATERIAL_DETAIL));
        assets.add(asset(5L, ProductImageSuiteAssetRole.USAGE_SCENE));
        return assets;
    }

    private ProductImageSuiteAssetRecord asset(Long id, ProductImageSuiteAssetRole role) {
        ProductImageSuiteAssetRecord asset = new ProductImageSuiteAssetRecord();
        asset.setId(id);
        asset.setImageRole(role);
        asset.setImageUrl("/assets/" + id + ".png");
        asset.setSha256("sha-" + id);
        return asset;
    }
}
