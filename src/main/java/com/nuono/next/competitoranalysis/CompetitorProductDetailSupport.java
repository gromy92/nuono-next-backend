package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

final class CompetitorProductDetailSupport {
    private final Clock clock;

    CompetitorProductDetailSupport(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    NoonProductDetail buildFallbackDetail(CompetitorProductRow product, String code) {
        if (product == null || !hasFallbackSnapshotData(product)) {
            return null;
        }
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(firstNonBlank(product.getNoonProductCode(), code));
        detail.setCodeType(product.getCodeType());
        detail.setDetailUrl(normalizeText(product.getCanonicalUrl()));
        detail.setTitleEn(normalizeText(firstNonBlank(product.getTitleEnSnapshot(), product.getTitleSnapshot())));
        detail.setTitleAr(normalizeText(product.getTitleArSnapshot()));
        detail.setBrand(normalizeText(product.getBrandSnapshot()));
        detail.setPriceAmount(product.getPriceAmountSnapshot());
        detail.setCurrencyCode(normalizeText(product.getCurrencyCodeSnapshot()));
        detail.setRating(product.getRatingSnapshot());
        detail.setReviewCount(product.getReviewCountSnapshot());
        detail.setMainImageUrlRaw(normalizeText(product.getImageUrlSnapshot()));
        detail.setMainImageUrlNormalized(normalizeText(product.getImageUrlSnapshot()));
        detail.setRawDetailJson("{\"source\":\"SEARCH_DISCOVERY_FALLBACK\"}");
        detail.setCapturedAt(LocalDateTime.now(clock));
        return detail;
    }

    NoonProductDetailRequest buildRequest(
            CompetitorWatchProductRow watchProduct,
            CompetitorProductRow product,
            String code
    ) {
        NoonProductDetailRequest request = new NoonProductDetailRequest();
        request.setSiteCode(normalizeText(watchProduct.getSiteCode()));
        request.setLocale(defaultLocale(watchProduct.getSiteCode()));
        request.setNoonProductCode(code);
        request.setCanonicalUrl(normalizeText(product == null ? null : product.getCanonicalUrl()));
        return request;
    }

    void normalizeDetail(NoonProductDetail detail, String fallbackCode, CompetitorProductRow product) {
        String code = normalizeCode(firstNonBlank(detail.getNoonProductCode(), fallbackCode));
        detail.setNoonProductCode(code);
        detail.setCodeType(firstNonBlank(
                detail.getCodeType(),
                product == null ? null : product.getCodeType(),
                NoonProductCodeSupport.codeType(code).orElse(null)
        ));
        detail.setDetailUrl(normalizeText(detail.getDetailUrl()));
        detail.setTitleEn(normalizeText(detail.getTitleEn()));
        detail.setTitleAr(normalizeText(detail.getTitleAr()));
        detail.setBrand(normalizeText(detail.getBrand()));
        detail.setSellerName(normalizeText(detail.getSellerName()));
        detail.setCurrencyCode(normalizeText(detail.getCurrencyCode()));
        detail.setMainImageUrlRaw(normalizeText(detail.getMainImageUrlRaw()));
        detail.setMainImageUrlNormalized(normalizeText(firstNonBlank(
                detail.getMainImageUrlNormalized(),
                detail.getMainImageUrlRaw()
        )));
        detail.setAvailabilityStatus(normalizeText(detail.getAvailabilityStatus()));
        detail.setSnapshotHash(firstNonBlank(detail.getSnapshotHash(), snapshotHash(detail)));
        if (detail.getCapturedAt() == null) {
            detail.setCapturedAt(LocalDateTime.now(clock));
        }
    }

    CompetitorProductInsertCommand buildProductUpdate(
            CompetitorProductRow product,
            NoonProductDetail detail,
            Long actorUserId
    ) {
        CompetitorProductInsertCommand command = new CompetitorProductInsertCommand();
        command.setId(product.getId());
        command.setWatchProductId(product.getWatchProductId());
        command.setNoonProductCode(detail.getNoonProductCode());
        command.setCodeType(detail.getCodeType());
        command.setCanonicalUrl(detail.getDetailUrl());
        command.setTitleSnapshot(firstNonBlank(detail.getTitleEn(), detail.getTitleAr()));
        command.setTitleEnSnapshot(detail.getTitleEn());
        command.setTitleArSnapshot(detail.getTitleAr());
        command.setBrandSnapshot(detail.getBrand());
        command.setImageUrlSnapshot(firstNonBlank(detail.getMainImageUrlNormalized(), detail.getMainImageUrlRaw()));
        command.setPriceAmountSnapshot(detail.getPriceAmount());
        command.setCurrencyCodeSnapshot(detail.getCurrencyCode());
        command.setRatingSnapshot(detail.getRating());
        command.setReviewCountSnapshot(detail.getReviewCount());
        command.setTagsSnapshotJson(firstNonBlank(detail.getBadgesJson(), detail.getLogisticsTagsJson()));
        command.setSourceType("PRODUCT_DETAIL");
        command.setActorUserId(actorUserId);
        return command;
    }

    String normalizeCode(String value) {
        return NoonProductCodeSupport.normalize(value);
    }

    private boolean hasFallbackSnapshotData(CompetitorProductRow product) {
        return StringUtils.hasText(product.getTitleSnapshot())
                || StringUtils.hasText(product.getBrandSnapshot())
                || StringUtils.hasText(product.getImageUrlSnapshot())
                || product.getPriceAmountSnapshot() != null
                || product.getRatingSnapshot() != null
                || product.getReviewCountSnapshot() != null;
    }

    private String defaultLocale(String siteCode) {
        String site = normalizeText(siteCode);
        if ("AE".equalsIgnoreCase(site) || "UAE".equalsIgnoreCase(site)) {
            return "en-AE";
        }
        if ("EG".equalsIgnoreCase(site) || "EGY".equalsIgnoreCase(site) || "EGYPT".equalsIgnoreCase(site)) {
            return "en-EG";
        }
        return "en-SA";
    }

    private String snapshotHash(NoonProductDetail detail) {
        String value = firstNonBlank(
                detail.getRawDetailJson(),
                detail.getNoonProductCode()
                        + "|"
                        + detail.getTitleEn()
                        + "|"
                        + detail.getPriceAmount()
                        + "|"
                        + detail.getCurrencyCode()
                        + "|"
                        + detail.getRating()
                        + "|"
                        + detail.getReviewCount()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "missing-detail-hash";
        }
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
