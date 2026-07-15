package com.nuono.next.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NoonImageTechnicalComplianceEvaluator {
    static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    static final int MIN_WIDTH_PX = 660;
    static final BigDecimal MIN_ASPECT_RATIO = new BigDecimal("0.5");
    static final BigDecimal MIN_PPI = new BigDecimal("72");
    static final String POLICY_VERSION = "2026-07-15";
    static final String POLICY_SOURCE = "https://helpcenter.noon.partners/en/category/product-listing/image-requirements-and-rejection-reasons-for-the-seller-sku";

    public NoonImageTechnicalComplianceView evaluate(ProductImageProfileAssetRecord asset) {
        List<NoonImageComplianceCheckView> checks = new ArrayList<>();
        checks.add(formatCheck(asset == null ? null : asset.getContentType()));
        checks.add(widthCheck(asset == null ? null : asset.getWidthPx()));
        checks.add(aspectRatioCheck(
                asset == null ? null : asset.getWidthPx(),
                asset == null ? null : asset.getHeightPx()
        ));
        checks.add(ppiCheck(
                asset == null ? null : asset.getHorizontalPpi(),
                asset == null ? null : asset.getVerticalPpi()
        ));
        checks.add(fileSizeCheck(asset == null ? null : asset.getSizeBytes()));
        checks.add(colorSpaceCheck(asset == null ? null : asset.getColorSpace()));

        NoonImageTechnicalComplianceView result = new NoonImageTechnicalComplianceView();
        result.setStatus(overallStatus(checks));
        result.setPolicyVersion(POLICY_VERSION);
        result.setPolicySource(POLICY_SOURCE);
        result.setChecks(checks);
        return result;
    }

    private NoonImageComplianceCheckView formatCheck(String contentType) {
        if (blank(contentType)) return unknown("FORMAT", "JPEG/JPG", "文件格式待确认");
        boolean pass = "image/jpeg".equalsIgnoreCase(contentType.trim()) || "image/jpg".equalsIgnoreCase(contentType.trim());
        return checked("FORMAT", pass, contentType, "JPEG/JPG", pass ? "格式符合" : "格式不是 JPEG/JPG");
    }

    private NoonImageComplianceCheckView widthCheck(Integer width) {
        if (width == null || width <= 0) return unknown("WIDTH", ">= 660px", "图片宽度待确认");
        boolean pass = width >= MIN_WIDTH_PX;
        return checked("WIDTH", pass, width + "px", ">= 660px", pass ? "宽度符合" : "宽度低于 660px");
    }

    private NoonImageComplianceCheckView aspectRatioCheck(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) {
            return unknown("ASPECT_RATIO", ">= 0.5", "宽高比待确认");
        }
        BigDecimal ratio = BigDecimal.valueOf(width)
                .divide(BigDecimal.valueOf(height), 3, RoundingMode.HALF_UP);
        boolean pass = ratio.compareTo(MIN_ASPECT_RATIO) >= 0;
        return checked("ASPECT_RATIO", pass, ratio.stripTrailingZeros().toPlainString(), ">= 0.5", pass ? "宽高比符合" : "宽高比低于 0.5");
    }

    private NoonImageComplianceCheckView ppiCheck(BigDecimal horizontalPpi, BigDecimal verticalPpi) {
        if (horizontalPpi == null || verticalPpi == null) return unknown("PPI", ">= 72", "PPI 待读取或人工确认");
        BigDecimal actual = horizontalPpi.min(verticalPpi);
        boolean pass = actual.compareTo(MIN_PPI) >= 0;
        return checked("PPI", pass, actual.stripTrailingZeros().toPlainString(), ">= 72", pass ? "PPI 符合" : "PPI 低于 72");
    }

    private NoonImageComplianceCheckView fileSizeCheck(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes < 0) return unknown("FILE_SIZE", "<= 10MB", "文件大小待确认");
        boolean pass = sizeBytes <= MAX_FILE_BYTES;
        String actual = BigDecimal.valueOf(sizeBytes)
                .divide(BigDecimal.valueOf(1024L * 1024L), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "MB";
        return checked("FILE_SIZE", pass, actual, "<= 10MB", pass ? "文件大小符合" : "文件超过 10MB");
    }

    private NoonImageComplianceCheckView colorSpaceCheck(String colorSpace) {
        if (blank(colorSpace)) return unknown("COLOR_SPACE", "RGB/sRGB", "色彩空间待确认");
        String normalized = colorSpace.trim().toUpperCase(Locale.ROOT);
        boolean pass = "RGB".equals(normalized) || "SRGB".equals(normalized);
        return checked("COLOR_SPACE", pass, colorSpace, "RGB/sRGB", pass ? "色彩空间符合" : "色彩空间不是 RGB/sRGB");
    }

    private String overallStatus(List<NoonImageComplianceCheckView> checks) {
        if (checks.stream().anyMatch(check -> "FAIL".equals(check.getStatus()))) return "FAIL";
        if (checks.stream().anyMatch(check -> "UNKNOWN".equals(check.getStatus()))) return "UNKNOWN";
        return "PASS";
    }

    private NoonImageComplianceCheckView checked(String key, boolean pass, String actual, String requirement, String message) {
        return new NoonImageComplianceCheckView(key, pass ? "PASS" : "FAIL", actual, requirement, message);
    }

    private NoonImageComplianceCheckView unknown(String key, String requirement, String message) {
        return new NoonImageComplianceCheckView(key, "UNKNOWN", null, requirement, message);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
