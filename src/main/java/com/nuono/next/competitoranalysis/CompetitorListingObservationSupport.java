package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.noon.NoonShanghaiBusinessTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.util.StringUtils;

final class CompetitorListingObservationSupport {
    private CompetitorListingObservationSupport() {
    }

    static CompetitorListingObservationCommand rankCommand(
            CompetitorKeywordRefreshContext context,
            NoonSearchPage page,
            NoonSearchResult result,
            Long id
    ) {
        CompetitorWatchProductRow watch = context.getWatchProduct();
        String code = normalizeCode(result.getNoonProductCode());
        LocalDateTime capturedAt =
                page == null || page.getCapturedAt() == null
                        ? NoonShanghaiBusinessTime.now()
                        : page.getCapturedAt();
        CompetitorListingObservationCommand command = baseCommand(
                watch,
                code,
                capturedAt.toLocalDate(),
                context.getActorUserId()
        );
        command.setId(id);
        command.setCanonicalUrl(normalizeText(result.getCanonicalUrl()));
        command.setTitleEn(normalizeText(firstNonBlank(
                result.getTitleEn(),
                result.getTitle()
        )));
        command.setTitleAr(normalizeText(result.getTitleAr()));
        command.setImageUrl(normalizeText(result.getImageUrl()));
        command.setPriceAmount(result.getPriceAmount());
        command.setCurrencyCode(normalizeText(result.getCurrencyCode()));
        command.setTagsJson(normalizeText(result.getTagsJson()));
        command.setSourceUrl(page == null
                ? null
                : normalizeText(page.getSourceUrl()));
        command.setParserVersion(page == null
                ? null
                : normalizeText(page.getParserVersion()));
        command.setProviderHttpStatus(page == null
                ? null
                : page.getProviderHttpStatus());
        command.setResponseHash(page == null
                ? null
                : normalizeText(page.getResponseHash()));
        command.setCapturedAt(capturedAt);
        return command;
    }

    static CompetitorListingObservationCommand baseCommand(
            CompetitorWatchProductRow watch,
            String code,
            LocalDate factDate,
            Long actorUserId
    ) {
        if (watch == null
                || watch.getOwnerUserId() == null
                || !StringUtils.hasText(watch.getStoreCode())
                || !StringUtils.hasText(watch.getSiteCode())
                || !StringUtils.hasText(code)) {
            throw new IllegalArgumentException(
                    "竞品列表观察缺少 owner/store/site/code。"
            );
        }
        CompetitorListingObservationCommand command =
                new CompetitorListingObservationCommand();
        command.setOwnerUserId(watch.getOwnerUserId());
        command.setStoreCode(normalizeUpper(watch.getStoreCode()));
        command.setSiteCode(normalizeUpper(watch.getSiteCode()));
        command.setNoonProductCode(code);
        command.setCodeType(NoonProductCodeSupport.codeType(code).orElseThrow(
                () -> new IllegalArgumentException(
                        "竞品列表观察商品码无效。"
                )
        ));
        command.setFactDate(factDate);
        command.setActorUserId(actorUserId);
        return command;
    }

    static CompetitorListingObservationCommand completionCommand(
            Long observationId,
            String leaseToken,
            NoonProductDetail detail,
            Long actorUserId
    ) {
        CompetitorListingObservationCommand command =
                new CompetitorListingObservationCommand();
        command.setId(observationId);
        command.setLeaseToken(leaseToken);
        command.setActorUserId(actorUserId);
        if (detail != null) {
            command.setProviderHttpStatus(
                    detail.getProviderHttpStatus()
            );
            command.setSourceUrl(normalizeText(
                    detail.getProviderSourceUrl()
            ));
            command.setParserVersion(normalizeText(
                    detail.getParserVersion()
            ));
            command.setResponseHash(normalizeText(
                    detail.getSnapshotHash()
            ));
            command.setCapturedAt(detail.getCapturedAt() == null
                    ? NoonShanghaiBusinessTime.now()
                    : detail.getCapturedAt());
        }
        return command;
    }

    static NoonProductDetail toDetail(
            CompetitorListingObservationRow row
    ) {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(row.getNoonProductCode());
        detail.setCodeType(row.getCodeType());
        detail.setDetailUrl(row.getCanonicalUrl());
        detail.setTitleEn(row.getTitleEn());
        detail.setTitleAr(row.getTitleAr());
        detail.setMainImageUrlRaw(row.getImageUrl());
        detail.setMainImageUrlNormalized(row.getImageUrl());
        detail.setPriceAmount(row.getPriceAmount());
        detail.setCurrencyCode(row.getCurrencyCode());
        detail.setBadgesJson(row.getTagsJson());
        detail.setProviderSourceUrl(row.getSourceUrl());
        detail.setParserVersion(row.getParserVersion());
        detail.setProviderHttpStatus(row.getProviderHttpStatus());
        detail.setSnapshotHash(row.getResponseHash());
        detail.setCapturedAt(row.getCapturedAt());
        detail.setAcquisitionMode(row.getAcquisitionMode());
        return detail;
    }

    static String leaseToken(Long taskId, String code) {
        return (taskId == null ? "manual" : String.valueOf(taskId))
                + ":"
                + code
                + ":"
                + UUID.randomUUID();
    }

    static String normalizeCode(String value) {
        return NoonProductCodeSupport.normalize(value);
    }

    static String normalizeUpper(String value) {
        return normalizeText(value).toUpperCase(Locale.ROOT);
    }

    static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    static String shrink(String value, int maxLength) {
        String normalized = normalizeText(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}
