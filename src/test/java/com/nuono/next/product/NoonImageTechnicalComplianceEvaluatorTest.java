package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NoonImageTechnicalComplianceEvaluatorTest {
    private final NoonImageTechnicalComplianceEvaluator evaluator = new NoonImageTechnicalComplianceEvaluator();

    @Test
    void exactMinimumBoundariesShouldPass() {
        ProductImageProfileAssetRecord asset = asset();
        asset.setWidthPx(660);
        asset.setHeightPx(1320);
        asset.setSizeBytes(10L * 1024L * 1024L);
        asset.setHorizontalPpi(new BigDecimal("72"));
        asset.setVerticalPpi(new BigDecimal("72"));

        NoonImageTechnicalComplianceView result = evaluator.evaluate(asset);

        assertEquals("PASS", result.getStatus());
    }

    @Test
    void anyDeterministicFailureShouldFailOverall() {
        ProductImageProfileAssetRecord asset = asset();
        asset.setWidthPx(659);

        NoonImageTechnicalComplianceView result = evaluator.evaluate(asset);

        assertEquals("FAIL", result.getStatus());
        assertEquals("FAIL", result.getChecks().stream()
                .filter(check -> "WIDTH".equals(check.getKey()))
                .findFirst()
                .orElseThrow()
                .getStatus());
    }

    @Test
    void missingPpiOrColorSpaceShouldRemainUnknownInsteadOfPassing() {
        ProductImageProfileAssetRecord asset = asset();
        asset.setHorizontalPpi(null);
        asset.setVerticalPpi(null);
        asset.setColorSpace(null);

        NoonImageTechnicalComplianceView result = evaluator.evaluate(asset);

        assertEquals("UNKNOWN", result.getStatus());
    }

    private ProductImageProfileAssetRecord asset() {
        ProductImageProfileAssetRecord asset = new ProductImageProfileAssetRecord();
        asset.setContentType("image/jpeg");
        asset.setSizeBytes(1024L * 1024L);
        asset.setWidthPx(1200);
        asset.setHeightPx(1600);
        asset.setHorizontalPpi(new BigDecimal("96"));
        asset.setVerticalPpi(new BigDecimal("96"));
        asset.setColorSpace("sRGB");
        return asset;
    }
}
