package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.util.StringUtils;

final class ProductImageReferenceSelector {

    private ProductImageReferenceSelector() {
    }

    static List<String> select(
            ProductImageProfileMapper mapper,
            ProductImageProfileRecord profile,
            ProductImageSuiteAssetRole role
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        List<ProductImageProfileAssetRecord> assets = mapper.selectAssets(profile.getId());
        ProductImageRole preferredRole = preferredSourceRole(role);
        assets.stream()
                .filter(asset -> asset.getImageRole() == preferredRole)
                .forEach(asset -> result.add(asset.getImageUrl()));
        assets.stream()
                .filter(asset -> asset.getImageRole() != preferredRole)
                .forEach(asset -> result.add(asset.getImageUrl()));
        if (profile.getProductMasterId() != null) {
            for (ProductImageProfileAssetRecord asset
                    : mapper.selectCurrentProductImages(profile.getProductMasterId())) {
                result.add(asset.getImageUrl());
            }
        }
        result.removeIf(value -> !StringUtils.hasText(value));
        return new ArrayList<>(result);
    }

    private static ProductImageRole preferredSourceRole(ProductImageSuiteAssetRole role) {
        if (role == ProductImageSuiteAssetRole.PACKAGE_LIST) return ProductImageRole.PACKAGE;
        if (role == ProductImageSuiteAssetRole.SIZE) return ProductImageRole.SIZE;
        if (role == ProductImageSuiteAssetRole.USAGE_SCENE) return ProductImageRole.SCENE;
        if (role == ProductImageSuiteAssetRole.CORE_FEATURE
                || role == ProductImageSuiteAssetRole.MATERIAL_DETAIL) {
            return ProductImageRole.DETAIL;
        }
        return ProductImageRole.MAIN;
    }
}
