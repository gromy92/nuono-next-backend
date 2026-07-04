package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailRequest;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CompetitorProductDetailRefreshService {
    private static final Logger log = LoggerFactory.getLogger(CompetitorProductDetailRefreshService.class);

    private final CompetitorAnalysisMapper mapper;
    private final NoonProductDetailAdapter detailAdapter;
    private final CompetitorProductSnapshotService snapshotService;
    private final Clock clock;

    @Autowired
    public CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            ObjectProvider<NoonProductDetailAdapter> detailAdapterProvider,
            ObjectProvider<CompetitorProductSnapshotService> snapshotServiceProvider
    ) {
        this(
                mapper,
                detailAdapterProvider == null ? null : detailAdapterProvider.getIfAvailable(),
                snapshotServiceProvider == null ? null : snapshotServiceProvider.getIfAvailable(),
                Clock.systemUTC()
        );
    }

    CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            Clock clock
    ) {
        this.mapper = mapper;
        this.detailAdapter = detailAdapter;
        this.snapshotService = snapshotService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public int refreshConfirmedCompetitors(
            CompetitorWatchProductRow watchProduct,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        if (watchProduct == null || watchProduct.getId() == null || detailAdapter == null || snapshotService == null) {
            return 0;
        }
        String selfCode = normalizeCode(watchProduct.getSelfNoonProductCode());
        int refreshed = refreshSelfDetail(watchProduct, selfCode, searchRunId, taskId, actorUserId);
        List<CompetitorProductRow> confirmedProducts =
                mapper.listConfirmedCompetitorProductsByWatchProductId(watchProduct.getId());
        Map<String, CompetitorProductRow> productsByCode = new LinkedHashMap<>();
        for (CompetitorProductRow product : confirmedProducts) {
            String code = normalizeCode(product == null ? null : product.getNoonProductCode());
            if (!StringUtils.hasText(code) || code.equals(selfCode) || NoonProductCodeSupport.codeType(code).isEmpty()) {
                continue;
            }
            productsByCode.putIfAbsent(code, product);
        }

        for (Map.Entry<String, CompetitorProductRow> entry : productsByCode.entrySet()) {
            String code = entry.getKey();
            CompetitorProductRow product = entry.getValue();
            try {
                NoonProductDetail detail = detailAdapter.fetch(buildRequest(watchProduct, product, code));
                if (detail == null) {
                    continue;
                }
                normalizeDetail(detail, code, product);
                mapper.updateCompetitorProductFromDetail(buildProductUpdate(product, detail, actorUserId));
                snapshotService.recordProductDetailSnapshot(watchProduct, product, detail, searchRunId, actorUserId);
                refreshed++;
            } catch (RuntimeException exception) {
                log.warn(
                        "competitor product detail refresh failed watchProductId={} competitorProductId={} noonProductCode={} taskId={} error={}",
                        watchProduct.getId(),
                        product == null ? null : product.getId(),
                        code,
                        taskId,
                        exception.getMessage(),
                        exception
                );
                if (recordFallbackSnapshot(watchProduct, product, code, searchRunId, actorUserId, taskId)) {
                    refreshed++;
                }
            }
        }
        return refreshed;
    }

    private int refreshSelfDetail(
            CompetitorWatchProductRow watchProduct,
            String selfCode,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        if (!StringUtils.hasText(selfCode) || NoonProductCodeSupport.codeType(selfCode).isEmpty()) {
            return 0;
        }
        try {
            NoonProductDetail detail = detailAdapter.fetch(buildRequest(watchProduct, null, selfCode));
            if (detail == null) {
                return 0;
            }
            normalizeDetail(detail, selfCode, null);
            snapshotService.recordProductDetailSnapshot(watchProduct, null, detail, searchRunId, actorUserId);
            return 1;
        } catch (RuntimeException exception) {
            log.warn(
                    "competitor self product detail refresh failed watchProductId={} noonProductCode={} taskId={} error={}",
                    watchProduct == null ? null : watchProduct.getId(),
                    selfCode,
                    taskId,
                    exception.getMessage(),
                    exception
            );
            return 0;
        }
    }

    private boolean recordFallbackSnapshot(
            CompetitorWatchProductRow watchProduct,
            CompetitorProductRow product,
            String code,
            Long searchRunId,
            Long actorUserId,
            Long taskId
    ) {
        NoonProductDetail fallback = buildFallbackDetail(product, code);
        if (fallback == null) {
            return false;
        }
        try {
            normalizeDetail(fallback, code, product);
            snapshotService.recordProductDetailSnapshot(watchProduct, product, fallback, searchRunId, actorUserId);
            return true;
        } catch (RuntimeException exception) {
            log.warn(
                    "competitor product detail fallback snapshot failed watchProductId={} competitorProductId={} noonProductCode={} taskId={} error={}",
                    watchProduct == null ? null : watchProduct.getId(),
                    product == null ? null : product.getId(),
                    code,
                    taskId,
                    exception.getMessage(),
                    exception
            );
            return false;
        }
    }

    private NoonProductDetail buildFallbackDetail(CompetitorProductRow product, String code) {
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

    private boolean hasFallbackSnapshotData(CompetitorProductRow product) {
        return StringUtils.hasText(product.getTitleSnapshot())
                || StringUtils.hasText(product.getBrandSnapshot())
                || StringUtils.hasText(product.getImageUrlSnapshot())
                || product.getPriceAmountSnapshot() != null
                || product.getRatingSnapshot() != null
                || product.getReviewCountSnapshot() != null;
    }

    private NoonProductDetailRequest buildRequest(
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

    private void normalizeDetail(NoonProductDetail detail, String fallbackCode, CompetitorProductRow product) {
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

    private CompetitorProductInsertCommand buildProductUpdate(
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

    private String normalizeCode(String value) {
        return NoonProductCodeSupport.normalize(value);
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
