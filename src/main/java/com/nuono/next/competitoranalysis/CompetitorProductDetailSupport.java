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
        detail.setCurrencyCode(normalizeText(detail.getCurrencyCode()));
        detail.setMainImageUrlRaw(normalizeText(detail.getMainImageUrlRaw()));
        detail.setMainImageUrlNormalized(normalizeText(firstNonBlank(
                detail.getMainImageUrlNormalized(),
                detail.getMainImageUrlRaw()
        )));
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
        command.setImageUrlSnapshot(firstNonBlank(detail.getMainImageUrlNormalized(), detail.getMainImageUrlRaw()));
        command.setPriceAmountSnapshot(detail.getPriceAmount());
        command.setCurrencyCodeSnapshot(detail.getCurrencyCode());
        command.setTagsSnapshotJson(firstNonBlank(detail.getBadgesJson(), detail.getLogisticsTagsJson()));
        command.setSourceType("LIST_EXACT_SEARCH");
        command.setActorUserId(actorUserId);
        return command;
    }

    String normalizeCode(String value) {
        return NoonProductCodeSupport.normalize(value);
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
                detail.getNoonProductCode()
                        + "|"
                        + detail.getTitleEn()
                        + "|"
                        + detail.getTitleAr()
                        + "|"
                        + detail.getPriceAmount()
                        + "|"
                        + detail.getCurrencyCode()
                        + "|"
                        + detail.getBadgesJson()
                        + "|"
                        + detail.getMainImageUrlNormalized()
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
