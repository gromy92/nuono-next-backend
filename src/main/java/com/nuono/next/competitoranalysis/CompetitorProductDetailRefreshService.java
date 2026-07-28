package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailRequest;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
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
    private final CompetitorProductDetailWriteGuard writeGuard;
    private final Clock clock;

    @Autowired
    public CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            ObjectProvider<NoonProductDetailAdapter> detailAdapterProvider,
            ObjectProvider<CompetitorProductSnapshotService> snapshotServiceProvider,
            CompetitorProductDetailWriteGuard writeGuard
    ) {
        this(
                mapper,
                detailAdapterProvider == null ? null : detailAdapterProvider.getIfAvailable(),
                snapshotServiceProvider == null ? null : snapshotServiceProvider.getIfAvailable(),
                writeGuard,
                Clock.systemUTC()
        );
    }
    CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            Clock clock
    ) {
        this(
                mapper,
                detailAdapter,
                snapshotService,
                new CompetitorProductDetailWriteGuard(mapper, snapshotService),
                clock
        );
    }

    CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            CompetitorProductDetailWriteGuard writeGuard,
            Clock clock
    ) {
        this.mapper = mapper;
        this.detailAdapter = detailAdapter;
        this.snapshotService = snapshotService;
        this.writeGuard = writeGuard;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public CompetitorProductDetailRefreshResult refreshConfirmedCompetitors(
            CompetitorWatchProductRow watchProduct,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        if (watchProduct == null || watchProduct.getId() == null) {
            return CompetitorProductDetailRefreshResult.unavailable(
                    "DETAIL_ADAPTER_UNAVAILABLE",
                    "竞品详情适配器或快照服务不可用。"
            );
        }
        List<CompetitorProductDetailTargetPlan.Entry> targets =
                CompetitorProductDetailTargetPlan.initial(mapper, watchProduct);
        if (detailAdapter == null || snapshotService == null) {
            return unavailableTargets(targets);
        }
        return refreshTargetContexts(watchProduct, targets, searchRunId, taskId, actorUserId);
    }

    public CompetitorProductDetailRefreshResult refreshTargets(
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTarget> targets,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        if (watchProduct == null || watchProduct.getId() == null) {
            return CompetitorProductDetailRefreshResult.unavailable(
                    "DETAIL_ADAPTER_UNAVAILABLE",
                    "竞品详情适配器或快照服务不可用。"
            );
        }
        List<CompetitorProductDetailTargetPlan.Entry> retryTargets =
                CompetitorProductDetailTargetPlan.retry(mapper, watchProduct, targets);
        if (detailAdapter == null || snapshotService == null) {
            return unavailableTargets(retryTargets);
        }
        return refreshTargetContexts(watchProduct, retryTargets, searchRunId, taskId, actorUserId);
    }

    private CompetitorProductDetailRefreshResult unavailableTargets(List<CompetitorProductDetailTargetPlan.Entry> targets) {
        if (targets == null || targets.isEmpty()) {
            return CompetitorProductDetailRefreshResult.unavailable(
                    "DETAIL_ADAPTER_UNAVAILABLE",
                    "竞品详情适配器或快照服务不可用。"
            );
        }
        CompetitorProductDetailRefreshResult result = CompetitorProductDetailRefreshResult.empty();
        for (CompetitorProductDetailTargetPlan.Entry target : targets) {
            if (target.recordTerminalFailure(result)) continue;
            result.recordFailure(
                    target.target,
                    "DETAIL_ADAPTER_UNAVAILABLE",
                    "竞品详情适配器或快照服务不可用。"
            );
        }
        return result;
    }

    private CompetitorProductDetailRefreshResult refreshTargetContexts(
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTargetPlan.Entry> targets,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        CompetitorProductDetailRefreshResult result = CompetitorProductDetailRefreshResult.empty();
        for (int index = 0; index < targets.size(); index++) {
            CompetitorProductDetailTargetPlan.Entry context = targets.get(index);
            CompetitorProductDetailTarget target = context.target;
            CompetitorProductRow product = context.product;
            String code = target.getNoonProductCode();
            result.recordAttempt(target);
            if (context.recordTerminalFailure(result)) continue;
            try {
                NoonProductDetail detail = detailAdapter.fetch(buildRequest(watchProduct, product, code));
                if (detail == null) {
                    throw new IllegalStateException("Noon 前台商品详情未返回结果。");
                }
                normalizeDetail(detail, code, product);
                if (!writeGuard.writeIfCurrent(
                        taskId,
                        watchProduct,
                        product,
                        target,
                        detail,
                        searchRunId,
                        actorUserId
                )) {
                    result.recordFailure(target, "DETAIL_TARGET_STALE", "详情写入前目标已发生变化。");
                    continue;
                }
                result.recordSuccess(target);
            } catch (CompetitorRefreshLeaseLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                String errorCode = errorCode(exception);
                String errorMessage = firstNonBlank(exception.getMessage(), "竞品详情抓取失败。");
                result.recordFailure(target, errorCode, errorMessage);
                log.warn(
                        "competitor product detail refresh failed watchProductId={} subjectType={} competitorProductId={} noonProductCode={} taskId={} error={}",
                        watchProduct.getId(),
                        target.getSubjectType(),
                        product == null ? null : product.getId(),
                        code,
                        taskId,
                        exception.getMessage(),
                        exception
                );
                if (result.hasRiskBackoffFailure()) {
                    for (int deferredIndex = index + 1; deferredIndex < targets.size(); deferredIndex++) {
                        if (targets.get(deferredIndex).recordTerminalFailure(result)) continue;
                        result.recordDeferred(
                                targets.get(deferredIndex).target,
                                errorCode,
                                errorMessage
                        );
                    }
                    break;
                }
            }
        }
        return result;
    }

    private String errorCode(RuntimeException exception) {
        return exception instanceof NoonSearchProviderException
                ? ((NoonSearchProviderException) exception).getErrorCode()
                : "DETAIL_REFRESH_FAILED";
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
            detail.setCapturedAt(com.nuono.next.noon.NoonShanghaiBusinessTime.now(clock));
        }
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
