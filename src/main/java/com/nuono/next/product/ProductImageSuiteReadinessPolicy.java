package com.nuono.next.product;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * Defines the minimum completed image set that may enter operator review.
 *
 * <p>The package image is intentionally not part of this gate. The review set is complete only
 * after the main image, both detail roles, size image and usage scene have durable asset metadata.
 * This matches the same asset identity fields required by the publish checkpoint.</p>
 */
final class ProductImageSuiteReadinessPolicy {
    private static final Set<ProductImageSuiteAssetRole> REQUIRED_REVIEW_ROLES = EnumSet.of(
            ProductImageSuiteAssetRole.MAIN,
            ProductImageSuiteAssetRole.SIZE,
            ProductImageSuiteAssetRole.CORE_FEATURE,
            ProductImageSuiteAssetRole.MATERIAL_DETAIL,
            ProductImageSuiteAssetRole.USAGE_SCENE
    );

    private ProductImageSuiteReadinessPolicy() {
    }

    static boolean isReadyForReview(List<ProductImageSuiteAssetRecord> assets) {
        Set<ProductImageSuiteAssetRole> completedRoles = EnumSet.noneOf(ProductImageSuiteAssetRole.class);
        if (assets != null) {
            for (ProductImageSuiteAssetRecord asset : assets) {
                if (isCompletedAsset(asset)) {
                    completedRoles.add(asset.getImageRole());
                }
            }
        }
        return completedRoles.containsAll(REQUIRED_REVIEW_ROLES);
    }

    static ProductImageSuiteStatus operatorFacingStatus(
            ProductImageSuiteStatus persistedStatus,
            List<ProductImageSuiteAssetRecord> assets
    ) {
        if (persistedStatus == ProductImageSuiteStatus.PENDING_REVIEW
                && !isReadyForReview(assets)) {
            return ProductImageSuiteStatus.GENERATING;
        }
        return persistedStatus;
    }

    private static boolean isCompletedAsset(ProductImageSuiteAssetRecord asset) {
        return asset != null
                && asset.getImageRole() != null
                && asset.getId() != null
                && StringUtils.hasText(asset.getImageUrl())
                && StringUtils.hasText(asset.getSha256());
    }
}
