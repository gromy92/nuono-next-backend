package com.nuono.next.productpublicdetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonPullFailureType;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ProductPublicDetailSyncService {
    public static final String TASK_TYPE = "PRODUCT_PUBLIC_DETAIL_SYNC";
    static final String SOURCE_PLATFORM = "NOON";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final ProductPublicDetailMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final Supplier<NoonPublicProductDetailAdapter> adapterSupplier;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int failureCooldownHours;
    private final NoonRiskBackoffGuard riskBackoffGuard;
    private final NoonPullFailurePolicy failurePolicy;
    @Autowired
    public ProductPublicDetailSyncService(
            ProductPublicDetailMapper mapper,
            OperationalTaskService operationalTaskService,
            ObjectProvider<NoonPublicProductDetailAdapter> adapterProvider,
            ObjectProvider<NoonRiskBackoffGuard> riskBackoffGuard,
            ObjectProvider<NoonPullFailurePolicy> failurePolicy,
            ObjectMapper objectMapper,
            @Value("${nuono.product-public-detail.scheduler.failure-cooldown-hours:12}") int failureCooldownHours
    ) {
        this(
                mapper,
                operationalTaskService,
                adapterProvider::getIfAvailable,
                objectMapper,
                Clock.systemUTC(),
                failureCooldownHours,
                riskBackoffGuard == null
                        ? NoonRiskBackoffGuard.disabled()
                        : riskBackoffGuard.getIfAvailable(NoonRiskBackoffGuard::disabled),
                failurePolicy == null ? new NoonPullFailurePolicy() : failurePolicy.getIfAvailable(NoonPullFailurePolicy::new)
        );
    }
    ProductPublicDetailSyncService(
            ProductPublicDetailMapper mapper,
            OperationalTaskService operationalTaskService,
            NoonPublicProductDetailAdapter adapter,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(
                mapper,
                operationalTaskService,
                () -> adapter,
                objectMapper,
                clock,
                0,
                NoonRiskBackoffGuard.disabled(),
                new NoonPullFailurePolicy(clock)
        );
    }
    ProductPublicDetailSyncService(
            ProductPublicDetailMapper mapper,
            OperationalTaskService operationalTaskService,
            Supplier<NoonPublicProductDetailAdapter> adapterSupplier,
            ObjectMapper objectMapper,
            Clock clock,
            int failureCooldownHours,
            NoonRiskBackoffGuard riskBackoffGuard,
            NoonPullFailurePolicy failurePolicy
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.adapterSupplier = adapterSupplier == null ? () -> null : adapterSupplier;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.failureCooldownHours = Math.max(0, failureCooldownHours);
        this.riskBackoffGuard = riskBackoffGuard == null ? NoonRiskBackoffGuard.disabled() : riskBackoffGuard;
        this.failurePolicy = failurePolicy == null ? new NoonPullFailurePolicy(this.clock) : failurePolicy;
    }
    public ProductPublicDetailStatusView syncStatus(BusinessAccessContext context, String storeCode, String siteCode) {
        String normalizedStore = normalizeStore(storeCode);
        String normalizedSite = normalizeSite(siteCode);
        Long ownerUserId = resolveOwnerUserId(context, normalizedStore);
        ScopeSelection selection = preferredScopeSelection(context, requireActiveScope(ownerUserId, normalizedStore, normalizedSite));
        String scopeStore = normalizeStore(selection.scope.getStoreCode());
        String scopeSite = normalizeSite(selection.scope.getSiteCode());
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        ProductPublicDetailStatusView view = new ProductPublicDetailStatusView();
        view.setOwnerUserId(ownerUserId);
        view.setStoreCode(scopeStore);
        view.setSiteCode(scopeSite);
        view.setCandidateCount(mapper.countCandidates(
                ownerUserId,
                scopeStore,
                scopeSite,
                failureCooldownHours,
                selection.enforcePreferredSite
        ));
        view.setLatestSucceededCount(mapper.countLatestByStatus(ownerUserId, scopeStore, scopeSite, ProductPublicDetailSyncStatus.SUCCEEDED));
        view.setLatestPartialCount(mapper.countLatestByStatus(ownerUserId, scopeStore, scopeSite, ProductPublicDetailSyncStatus.PARTIAL));
        view.setLatestNotFoundCount(mapper.countLatestByStatus(ownerUserId, scopeStore, scopeSite, ProductPublicDetailSyncStatus.NOT_FOUND));
        view.setLatestFailedCount(mapper.countLatestByStatus(ownerUserId, scopeStore, scopeSite, ProductPublicDetailSyncStatus.FAILED));
        view.setTodaySucceededCount(mapper.countTodayByStatus(ownerUserId, scopeStore, scopeSite, ProductPublicDetailSyncStatus.SUCCEEDED, today));
        view.setTodayPartialCount(mapper.countTodayByStatus(ownerUserId, scopeStore, scopeSite, ProductPublicDetailSyncStatus.PARTIAL, today));
        view.setTodayNotFoundCount(mapper.countTodayByStatus(ownerUserId, scopeStore, scopeSite, ProductPublicDetailSyncStatus.NOT_FOUND, today));
        view.setTodayFailedCount(mapper.countTodayByStatus(ownerUserId, scopeStore, scopeSite, ProductPublicDetailSyncStatus.FAILED, today));
        view.setLatestTask(ProductPublicDetailTaskView.from(findLatestTask(ownerUserId, scopeStore, scopeSite)));
        view.setCheckedAt(now());
        return view;
    }
    public ProductPublicDetailSnapshot latest(
            BusinessAccessContext context,
            String storeCode,
            String siteCode,
            Long productMasterId,
            Long productVariantId
    ) {
        if (productMasterId == null || productVariantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productMasterId and productVariantId are required.");
        }
        String normalizedStore = normalizeStore(storeCode);
        String normalizedSite = normalizeSite(siteCode);
        Long ownerUserId = resolveOwnerUserId(context, normalizedStore);
        requireActiveScope(ownerUserId, normalizedStore, normalizedSite);
        return mapper.selectLatestSnapshot(ownerUserId, normalizedStore, normalizedSite, productMasterId, productVariantId);
    }
    Optional<NoonRiskBackoffHold> recordRiskBackoffIfNeeded(
            Long taskId,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            ProductPublicDetailSnapshot snapshot
    ) {
        if (snapshot == null || snapshot.getSyncStatus() != ProductPublicDetailSyncStatus.FAILED) {
            return Optional.empty();
        }
        NoonPullFailureType failureType = failurePolicy.classify(riskDiagnostic(snapshot));
        if (!isRiskBackoffFailure(failureType)) {
            return Optional.empty();
        }
        NoonRiskBackoffHold hold = riskBackoffGuard.recordRiskSignal(
                publicDetailScope(ownerUserId, storeCode, siteCode),
                failureType.code(),
                "PUBLIC_DETAIL",
                taskId,
                null,
                riskDiagnostic(snapshot)
        );
        return Optional.of(hold);
    }
    private boolean isRiskBackoffFailure(NoonPullFailureType failureType) {
        return failureType == NoonPullFailureType.RATE_LIMITED
                || failureType == NoonPullFailureType.CAPTCHA_REQUIRED
                || failureType == NoonPullFailureType.BLOCKED_BY_RISK_CONTROL;
    }
    private NoonRiskBackoffScope publicDetailScope(Long ownerUserId, String storeCode, String siteCode) {
        return NoonRiskBackoffScope.publicDetail(ownerUserId, storeCode, siteCode);
    }
    NoonPublicProductDetailAdapter adapter() {
        return adapterSupplier.get();
    }
    Optional<NoonRiskBackoffHold> currentRiskBackoffHold(Long ownerUserId, String storeCode, String siteCode) {
        return riskBackoffGuard.currentHold(publicDetailScope(ownerUserId, storeCode, siteCode));
    }
    private String riskDiagnostic(ProductPublicDetailSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        return String.join(" ",
                text(snapshot.getFailureCode()),
                text(snapshot.getFailureMessage()),
                snapshot.getProviderHttpStatus() == null ? "" : "http " + snapshot.getProviderHttpStatus()
        ).trim();
    }
    void upsertSnapshot(ProductPublicDetailSnapshot snapshot) {
        if (snapshot == null || snapshot.getSyncStatus() == null) {
            return;
        }
        boolean updatesLatestPointer = snapshot.getSyncStatus().updatesLatestPointer();
        ProductPublicDetailSnapshot existing = mapper.selectDailySnapshot(
                snapshot.getProductMasterId(),
                snapshot.getProductVariantId(),
                normalizeSite(snapshot.getSiteCode()),
                SOURCE_PLATFORM,
                snapshot.getFactDate()
        );
        if (existing == null) {
            snapshot.setId(mapper.nextSnapshotId());
            snapshot.setLatest(updatesLatestPointer);
            mapper.insertSnapshot(snapshot);
        } else {
            if (shouldPreserveExistingLatestSuccess(existing, snapshot)) {
                return;
            }
            snapshot.setId(existing.getId());
            snapshot.setLatest(existing.getLatest());
            mapper.updateSnapshotPreservingTrustedData(snapshot);
        }
        if (updatesLatestPointer) {
            mapper.clearLatestForProduct(
                    snapshot.getProductMasterId(),
                    snapshot.getProductVariantId(),
                    normalizeSite(snapshot.getSiteCode()),
                    SOURCE_PLATFORM,
                    snapshot.getId(),
                    snapshot.getUpdatedBy()
            );
            mapper.markLatest(snapshot.getId(), snapshot.getUpdatedBy());
        }
    }
    private boolean shouldPreserveExistingLatestSuccess(ProductPublicDetailSnapshot existing,
                                                       ProductPublicDetailSnapshot incoming) {
        return Boolean.TRUE.equals(existing.getLatest())
                && existing.getSyncStatus() != null
                && existing.getSyncStatus().updatesLatestPointer()
                && incoming.getSyncStatus() != null
                && !incoming.getSyncStatus().updatesLatestPointer();
    }
    ProductPublicDetailSnapshot toSnapshot(ProductPublicDetailCandidate candidate, NoonPublicProductDetailResult result, Long actorUserId) {
        LocalDate factDate = LocalDate.now(BUSINESS_ZONE);
        ProductPublicDetailSnapshot snapshot = new ProductPublicDetailSnapshot();
        snapshot.setOwnerUserId(candidate.getOwnerUserId());
        snapshot.setLogicalStoreId(candidate.getLogicalStoreId());
        snapshot.setStoreCode(normalizeStore(candidate.getStoreCode()));
        snapshot.setSiteCode(normalizeSite(candidate.getSiteCode()));
        snapshot.setProductMasterId(candidate.getProductMasterId());
        snapshot.setProductVariantId(candidate.getProductVariantId());
        snapshot.setProductSiteOfferId(candidate.getProductSiteOfferId());
        snapshot.setPartnerSku(trim(candidate.getPartnerSku()));
        snapshot.setSkuParent(trim(candidate.getSkuParent()));
        snapshot.setNoonProductCode(NoonProductCodeSupport.normalize(firstText(result.getNoonProductCode(), candidate.getNoonProductCode())));
        snapshot.setCodeType(trim(firstText(result.getCodeType(), NoonProductCodeSupport.codeType(snapshot.getNoonProductCode()).orElse(null))));
        snapshot.setSourcePlatform(SOURCE_PLATFORM);
        snapshot.setTitleEn(trim(result.getTitleEn()));
        snapshot.setTitleAr(trim(result.getTitleAr()));
        snapshot.setBrand(trim(result.getBrand()));
        snapshot.setCategoryPath(trim(result.getCategoryPath()));
        snapshot.setPriceAmount(result.getPriceAmount());
        snapshot.setCurrencyCode(trim(result.getCurrencyCode()));
        snapshot.setRating(result.getRating());
        snapshot.setReviewCount(result.getReviewCount());
        snapshot.setAvailabilityText(trim(result.getAvailabilityText()));
        snapshot.setMainImageUrl(trim(result.getMainImageUrl()));
        snapshot.setDetailUrl(trim(result.getDetailUrl()));
        snapshot.setRawPayloadJson(trim(result.getRawPayloadJson()));
        snapshot.setProviderHttpStatus(result.getProviderHttpStatus());
        snapshot.setProviderSourceUrl(trim(result.getProviderSourceUrl()));
        snapshot.setProviderResponseHash(trim(result.getProviderResponseHash()));
        snapshot.setProviderParserVersion(trim(result.getProviderParserVersion()));
        snapshot.setSyncStatus(result.getStatus() == null ? ProductPublicDetailSyncStatus.FAILED : result.getStatus());
        snapshot.setFailureCode(trim(result.getFailureCode()));
        snapshot.setFailureMessage(shrink(result.getFailureMessage(), 1000));
        snapshot.setFactDate(factDate);
        snapshot.setFetchedAt(result.getFetchedAt() == null ? now() : result.getFetchedAt());
        snapshot.setCreatedBy(actorUserId);
        snapshot.setUpdatedBy(actorUserId);
        snapshot.setSnapshotHash(snapshotHash(snapshot));
        return snapshot;
    }
    NoonPublicProductDetailResult failureResult(
            String code,
            String failureCode,
            String failureMessage,
            Integer providerHttpStatus,
            String providerSourceUrl,
            String providerResponseHash,
            String providerParserVersion
    ) {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(ProductPublicDetailSyncStatus.FAILED);
        result.setNoonProductCode(code);
        result.setCodeType(NoonProductCodeSupport.codeType(code).orElse(null));
        result.setFailureCode(failureCode);
        result.setFailureMessage(failureMessage);
        result.setProviderHttpStatus(providerHttpStatus);
        result.setProviderSourceUrl(providerSourceUrl);
        result.setProviderResponseHash(providerResponseHash);
        result.setProviderParserVersion(providerParserVersion);
        result.setFetchedAt(now());
        return result;
    }
    private ProductPublicDetailScope requireActiveScope(Long ownerUserId, String storeCode, String siteCode) {
        if (ownerUserId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "业务 owner 不明确，无法同步商品前台详情。");
        }
        ProductPublicDetailScope scope = mapper.selectActiveScope(ownerUserId, storeCode, siteCode);
        if (scope == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到 active 店铺站点范围。");
        }
        return scope;
    }
    private ScopeSelection preferredScopeSelection(BusinessAccessContext context, ProductPublicDetailScope requestedScope) {
        if (requestedScope == null || requestedScope.getOwnerUserId() == null || requestedScope.getLogicalStoreId() == null) {
            return ScopeSelection.enforcing(requestedScope);
        }
        ProductPublicDetailScope preferred = mapper.selectPreferredScope(
                requestedScope.getOwnerUserId(),
                requestedScope.getLogicalStoreId(),
                failureCooldownHours
        );
        if (preferred == null || context == null || !StringUtils.hasText(preferred.getStoreCode())) {
            return ScopeSelection.enforcing(requestedScope);
        }
        String preferredStore = normalizeStore(preferred.getStoreCode());
        return context.canAccessStore(preferredStore)
                ? ScopeSelection.enforcing(preferred)
                : ScopeSelection.requestedWithoutPreferredExclusion(requestedScope);
    }
    private static final class ScopeSelection {
        private final ProductPublicDetailScope scope;
        private final boolean enforcePreferredSite;
        private ScopeSelection(ProductPublicDetailScope scope, boolean enforcePreferredSite) {
            this.scope = scope;
            this.enforcePreferredSite = enforcePreferredSite;
        }
        private static ScopeSelection enforcing(ProductPublicDetailScope scope) {
            return new ScopeSelection(scope, true);
        }
        private static ScopeSelection requestedWithoutPreferredExclusion(ProductPublicDetailScope scope) {
            return new ScopeSelection(scope, false);
        }
    }
    private Long resolveOwnerUserId(BusinessAccessContext context, String storeCode) {
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少业务访问上下文。");
        }
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null ? context.getBusinessOwnerUserId() : ownerUserId;
    }
    private OperationalTask findLatestTask(Long ownerUserId, String storeCode, String siteCode) {
        return operationalTaskService.listRecent(TASK_TYPE, 200).stream()
                .filter(task -> sameScope(task, ownerUserId, storeCode, siteCode))
                .findFirst()
                .orElse(null);
    }
    private boolean sameScope(OperationalTask task, Long ownerUserId, String storeCode, String siteCode) {
        return task != null
                && Objects.equals(task.getOwnerUserId(), ownerUserId)
                && Objects.equals(normalizeStore(task.getStoreCode()), storeCode)
                && Objects.equals(normalizeSite(task.getSiteCode()), siteCode);
    }
    String defaultLocale(String siteCode) {
        String site = normalizeSite(siteCode);
        if ("AE".equals(site) || "UAE".equals(site)) {
            return "en-AE";
        }
        if ("EG".equals(site) || "EGY".equals(site)) {
            return "en-EG";
        }
        return "en-SA";
    }
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), BUSINESS_ZONE);
    }
    private String snapshotHash(ProductPublicDetailSnapshot snapshot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", snapshot.getSyncStatus());
        values.put("code", snapshot.getNoonProductCode());
        values.put("titleEn", snapshot.getTitleEn());
        values.put("titleAr", snapshot.getTitleAr());
        values.put("brand", snapshot.getBrand());
        values.put("categoryPath", snapshot.getCategoryPath());
        values.put("priceAmount", snapshot.getPriceAmount());
        values.put("currencyCode", snapshot.getCurrencyCode());
        values.put("rating", snapshot.getRating());
        values.put("reviewCount", snapshot.getReviewCount());
        values.put("availabilityText", snapshot.getAvailabilityText());
        values.put("mainImageUrl", snapshot.getMainImageUrl());
        values.put("detailUrl", snapshot.getDetailUrl());
        values.put("failureCode", snapshot.getFailureCode());
        values.put("failureMessage", snapshot.getFailureMessage());
        return sha256(toJson(values));
    }
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return null;
        }
    }
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
    String normalizeStore(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
    String normalizeSite(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteCode is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
    String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }
    String shrink(String value, int maxLength) {
        String text = StringUtils.hasText(value) ? value.replaceAll("\\s+", " ").trim() : "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
