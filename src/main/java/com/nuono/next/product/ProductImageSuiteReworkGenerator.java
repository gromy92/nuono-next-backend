package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

final class ProductImageSuiteReworkGenerator {

    private ProductImageSuiteReworkGenerator() {
    }

    static void replace(
            ProductImageProfileMapper mapper,
            ProductImageGenerator generator,
            ProductImageSuiteRecord suite,
            List<ProductImageSuiteReviewTargetRecord> reviewTargets,
            List<ProductImageSuiteAssetRecord> existing,
            List<String> baseReferences,
            String storeCode,
            PromptFactory promptFactory,
            GeneratedAssetWriter writer
    ) {
        if (reviewTargets.isEmpty() || existing.isEmpty()) return;
        boolean wholeSuite = reviewTargets.stream()
                .anyMatch(target -> "SUITE".equalsIgnoreCase(target.getTargetScope()));
        Set<Long> selectedIds = reviewTargets.stream()
                .map(ProductImageSuiteReviewTargetRecord::getAssetId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (ProductImageSuiteAssetRecord asset : existing) {
            if (!wholeSuite && !selectedIds.contains(asset.getId())) continue;
            List<String> references = new ArrayList<>(baseReferences);
            if (StringUtils.hasText(asset.getImageUrl())) references.add(0, asset.getImageUrl());
            GeneratedProductImage image = generator.generate(
                    promptFactory.create(suite, asset.getImageRole()),
                    references
            );
            ProductImageSuiteAssetRecord replacement = writer.save(
                    suite,
                    asset.getImageRole(),
                    image,
                    storeCode,
                    asset.getSortOrder() == null ? 10 : asset.getSortOrder()
            );
            mapper.updateSuiteAssetContent(
                    suite.getId(),
                    asset.getId(),
                    replacement.getImageUrl(),
                    replacement.getContentType(),
                    replacement.getSizeBytes(),
                    replacement.getSha256()
            );
        }
    }

    @FunctionalInterface
    interface PromptFactory {
        String create(ProductImageSuiteRecord suite, ProductImageSuiteAssetRole role);
    }

    @FunctionalInterface
    interface GeneratedAssetWriter {
        ProductImageSuiteAssetRecord save(
                ProductImageSuiteRecord suite,
                ProductImageSuiteAssetRole role,
                GeneratedProductImage generated,
                String storeCode,
                int sortOrder
        );
    }
}
