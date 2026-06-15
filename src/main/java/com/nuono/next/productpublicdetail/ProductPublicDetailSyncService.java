package com.nuono.next.productpublicdetail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailRequest;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final TaskSubmitter taskSubmitter;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maxProductsPerTask;
    private final int staleDays;
    private final int failureCooldownHours;

    @Autowired
    public ProductPublicDetailSyncService(
            ProductPublicDetailMapper mapper,
            OperationalTaskService operationalTaskService,
            ObjectProvider<NoonAccountTaskQueue> queueProvider,
            ObjectProvider<NoonPublicProductDetailAdapter> adapterProvider,
            ObjectMapper objectMapper,
            @Value("${nuono.product-public-detail.scheduler.max-products-per-task:100}") int maxProductsPerTask,
            @Value("${nuono.product-public-detail.scheduler.stale-days:1}") int staleDays,
            @Value("${nuono.product-public-detail.scheduler.failure-cooldown-hours:12}") int failureCooldownHours
    ) {
        this(
                mapper,
                operationalTaskService,
                adapterProvider::getIfAvailable,
                (accountKey, task) -> {
                    NoonAccountTaskQueue queue = queueProvider.getIfAvailable();
                    if (queue == null) {
                        task.run();
                    } else {
                        queue.submit(accountKey, task);
                    }
                },
                objectMapper,
                Clock.systemUTC(),
                maxProductsPerTask,
                staleDays,
                failureCooldownHours
        );
    }

    ProductPublicDetailSyncService(
            ProductPublicDetailMapper mapper,
            OperationalTaskService operationalTaskService,
            NoonPublicProductDetailAdapter adapter,
            TaskSubmitter taskSubmitter,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(mapper, operationalTaskService, () -> adapter, taskSubmitter, objectMapper, clock, 100, 1, 12);
    }

    ProductPublicDetailSyncService(
            ProductPublicDetailMapper mapper,
            OperationalTaskService operationalTaskService,
            Supplier<NoonPublicProductDetailAdapter> adapterSupplier,
            TaskSubmitter taskSubmitter,
            ObjectMapper objectMapper,
            Clock clock,
            int maxProductsPerTask,
            int staleDays,
            int failureCooldownHours
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.adapterSupplier = adapterSupplier == null ? () -> null : adapterSupplier;
        this.taskSubmitter = taskSubmitter == null ? (key, task) -> task.run() : taskSubmitter;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.maxProductsPerTask = Math.max(1, Math.min(maxProductsPerTask, 500));
        this.staleDays = Math.max(1, staleDays);
        this.failureCooldownHours = Math.max(1, failureCooldownHours);
    }

    public ProductPublicDetailTaskView submitManual(BusinessAccessContext context, String storeCode, String siteCode) {
        String normalizedStore = normalizeStore(storeCode);
        String normalizedSite = normalizeSite(siteCode);
        Long ownerUserId = resolveOwnerUserId(context, normalizedStore);
        requireActiveScope(ownerUserId, normalizedStore, normalizedSite);
        OperationalTask active = findActiveTask(ownerUserId, normalizedStore, normalizedSite);
        if (active != null) {
            return ProductPublicDetailTaskView.from(active);
        }
        return ProductPublicDetailTaskView.from(submitTask(ownerUserId, normalizedStore, normalizedSite, context.getSessionUserId(), false));
    }

    public ProductPublicDetailTaskView submitScheduled(Long ownerUserId, String storeCode, String siteCode) {
        String normalizedStore = normalizeStore(storeCode);
        String normalizedSite = normalizeSite(siteCode);
        requireActiveScope(ownerUserId, normalizedStore, normalizedSite);
        OperationalTask active = findActiveTask(ownerUserId, normalizedStore, normalizedSite);
        if (active != null) {
            return ProductPublicDetailTaskView.from(active);
        }
        return ProductPublicDetailTaskView.from(submitTask(ownerUserId, normalizedStore, normalizedSite, ownerUserId, true));
    }

    public ProductPublicDetailStatusView syncStatus(BusinessAccessContext context, String storeCode, String siteCode) {
        String normalizedStore = normalizeStore(storeCode);
        String normalizedSite = normalizeSite(siteCode);
        Long ownerUserId = resolveOwnerUserId(context, normalizedStore);
        requireActiveScope(ownerUserId, normalizedStore, normalizedSite);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        ProductPublicDetailStatusView view = new ProductPublicDetailStatusView();
        view.setOwnerUserId(ownerUserId);
        view.setStoreCode(normalizedStore);
        view.setSiteCode(normalizedSite);
        view.setCandidateCount(mapper.countCandidates(ownerUserId, normalizedStore, normalizedSite));
        view.setLatestSucceededCount(mapper.countLatestByStatus(ownerUserId, normalizedStore, normalizedSite, ProductPublicDetailSyncStatus.SUCCEEDED));
        view.setLatestPartialCount(mapper.countLatestByStatus(ownerUserId, normalizedStore, normalizedSite, ProductPublicDetailSyncStatus.PARTIAL));
        view.setLatestNotFoundCount(mapper.countLatestByStatus(ownerUserId, normalizedStore, normalizedSite, ProductPublicDetailSyncStatus.NOT_FOUND));
        view.setLatestFailedCount(mapper.countLatestByStatus(ownerUserId, normalizedStore, normalizedSite, ProductPublicDetailSyncStatus.FAILED));
        view.setTodaySucceededCount(mapper.countTodayByStatus(ownerUserId, normalizedStore, normalizedSite, ProductPublicDetailSyncStatus.SUCCEEDED, today));
        view.setTodayPartialCount(mapper.countTodayByStatus(ownerUserId, normalizedStore, normalizedSite, ProductPublicDetailSyncStatus.PARTIAL, today));
        view.setTodayNotFoundCount(mapper.countTodayByStatus(ownerUserId, normalizedStore, normalizedSite, ProductPublicDetailSyncStatus.NOT_FOUND, today));
        view.setTodayFailedCount(mapper.countTodayByStatus(ownerUserId, normalizedStore, normalizedSite, ProductPublicDetailSyncStatus.FAILED, today));
        view.setLatestTask(ProductPublicDetailTaskView.from(findLatestTask(ownerUserId, normalizedStore, normalizedSite)));
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

    void runTask(Long taskId, Long ownerUserId, String storeCode, String siteCode, Long actorUserId, boolean scheduled) {
        long startedNanos = System.nanoTime();
        ProductPublicDetailSyncSummary summary = new ProductPublicDetailSyncSummary();
        NoonPublicProductDetailAdapter adapter = adapterSupplier.get();
        if (adapter == null) {
            operationalTaskService.fail(taskId, "PRODUCT_PUBLIC_DETAIL_ADAPTER_UNAVAILABLE", "Noon 前台公开详情 adapter 不可用。");
            return;
        }
        summary.setAdapterVersion(adapter.adapterVersion());
        try {
            List<ProductPublicDetailCandidate> candidates = mapper.listCandidates(
                    ownerUserId,
                    storeCode,
                    siteCode,
                    maxProductsPerTask,
                    staleDays,
                    failureCooldownHours,
                    scheduled
            );
            summary.setSelected(candidates.size());
            if (candidates.isEmpty()) {
                summary.setElapsedMillis(elapsedMillis(startedNanos));
                operationalTaskService.complete(taskId, toJson(summary), "商品前台详情同步完成：没有符合条件的候选商品。");
                return;
            }
            int index = 0;
            for (ProductPublicDetailCandidate candidate : candidates) {
                index++;
                String code = NoonProductCodeSupport.normalize(candidate.getNoonProductCode());
                if (!StringUtils.hasText(code) || NoonProductCodeSupport.codeType(code).isEmpty()) {
                    summary.setSkipped(summary.getSkipped() + 1);
                    continue;
                }
                try {
                    NoonPublicProductDetailResult result = adapter.fetch(NoonPublicProductDetailRequest.builder()
                            .siteCode(candidate.getSiteCode())
                            .locale(defaultLocale(candidate.getSiteCode()))
                            .noonProductCode(code)
                            .build());
                    if (result == null) {
                        result = failureResult(code, "PROVIDER_EMPTY_RESPONSE", "Noon 前台公开详情 adapter 返回空结果。", null, null, null, null);
                    }
                    ProductPublicDetailSnapshot snapshot = toSnapshot(candidate, result, actorUserId);
                    upsertSnapshot(snapshot);
                    summary.increment(snapshot.getSyncStatus());
                } catch (Exception exception) {
                    ProductPublicDetailSnapshot snapshot = toSnapshot(
                            candidate,
                            failureResult(code, "PROVIDER_EXCEPTION", shrink(exception.getMessage(), 300), null, null, null, null),
                            actorUserId
                    );
                    upsertSnapshot(snapshot);
                    summary.increment(ProductPublicDetailSyncStatus.FAILED);
                }
                operationalTaskService.progress(taskId, progress(index, candidates.size()), progressMessage(index, candidates.size(), summary));
            }
            summary.setElapsedMillis(elapsedMillis(startedNanos));
            operationalTaskService.complete(taskId, toJson(summary), completeMessage(summary));
        } catch (Exception exception) {
            operationalTaskService.fail(taskId, "PRODUCT_PUBLIC_DETAIL_SYNC_FAILED", shrink(exception.getMessage(), 500));
        }
    }

    private OperationalTask submitTask(Long ownerUserId, String storeCode, String siteCode, Long actorUserId, boolean scheduled) {
        String naturalKey = scheduled ? scheduledNaturalKey(ownerUserId, storeCode, siteCode) : manualNaturalKey(ownerUserId, storeCode, siteCode);
        OperationalTaskPayload payload = OperationalTaskPayload.builder()
                .ownerUserId(ownerUserId)
                .storeCode(storeCode)
                .siteCode(siteCode)
                .payloadJson(toJson(Map.of(
                        "ownerUserId", ownerUserId,
                        "storeCode", storeCode,
                        "siteCode", siteCode,
                        "trigger", scheduled ? "SCHEDULED" : "MANUAL"
                )))
                .message("商品前台详情同步正在后台执行。")
                .build();
        OperationalTask task = operationalTaskService.start(TASK_TYPE, naturalKey, payload);
        taskSubmitter.submit(accountKey(ownerUserId, storeCode), () -> runTask(task.getId(), ownerUserId, storeCode, siteCode, actorUserId, scheduled));
        return task;
    }

    private void upsertSnapshot(ProductPublicDetailSnapshot snapshot) {
        ProductPublicDetailSnapshot existing = mapper.selectDailySnapshot(
                snapshot.getProductMasterId(),
                snapshot.getProductVariantId(),
                normalizeSite(snapshot.getSiteCode()),
                SOURCE_PLATFORM,
                snapshot.getFactDate()
        );
        if (existing == null) {
            snapshot.setId(mapper.nextSnapshotId());
            snapshot.setLatest(snapshot.getSyncStatus() != null && snapshot.getSyncStatus().updatesLatestPointer());
            mapper.insertSnapshot(snapshot);
        } else {
            snapshot.setId(existing.getId());
            snapshot.setLatest(existing.getLatest());
            mapper.updateSnapshotPreservingTrustedData(snapshot);
        }
        if (snapshot.getSyncStatus() != null && snapshot.getSyncStatus().updatesLatestPointer()) {
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

    private ProductPublicDetailSnapshot toSnapshot(ProductPublicDetailCandidate candidate, NoonPublicProductDetailResult result, Long actorUserId) {
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

    private NoonPublicProductDetailResult failureResult(
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

    private Long resolveOwnerUserId(BusinessAccessContext context, String storeCode) {
        if (context == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少业务访问上下文。");
        }
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null ? context.getBusinessOwnerUserId() : ownerUserId;
    }

    private OperationalTask findActiveTask(Long ownerUserId, String storeCode, String siteCode) {
        return operationalTaskService.listActive(TASK_TYPE, 1000).stream()
                .filter(task -> sameScope(task, ownerUserId, storeCode, siteCode))
                .findFirst()
                .orElse(null);
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

    private String scheduledNaturalKey(Long ownerUserId, String storeCode, String siteCode) {
        return "product-public-detail:" + ownerUserId + ":" + storeCode + ":" + siteCode + ":" + LocalDate.now(BUSINESS_ZONE);
    }

    private String manualNaturalKey(Long ownerUserId, String storeCode, String siteCode) {
        return "product-public-detail:" + ownerUserId + ":" + storeCode + ":" + siteCode + ":manual:" + System.currentTimeMillis();
    }

    private String accountKey(Long ownerUserId, String storeCode) {
        return ownerUserId + ":" + storeCode;
    }

    private String defaultLocale(String siteCode) {
        String site = normalizeSite(siteCode);
        if ("AE".equals(site) || "UAE".equals(site)) {
            return "en-AE";
        }
        if ("EG".equals(site) || "EGY".equals(site)) {
            return "en-EG";
        }
        return "en-SA";
    }

    private int progress(int index, int total) {
        if (total <= 0) {
            return 100;
        }
        return Math.max(1, Math.min(99, (int) Math.floor(index * 100.0 / total)));
    }

    private String progressMessage(int index, int total, ProductPublicDetailSyncSummary summary) {
        return "商品前台详情同步中：" + index + "/" + total
                + "，成功 " + summary.getSucceeded()
                + "，部分 " + summary.getPartial()
                + "，未找到 " + summary.getNotFound()
                + "，失败 " + summary.getFailed()
                + "，跳过 " + summary.getSkipped() + "。";
    }

    private String completeMessage(ProductPublicDetailSyncSummary summary) {
        return "商品前台详情同步完成：成功 " + summary.getSucceeded()
                + "，部分 " + summary.getPartial()
                + "，未找到 " + summary.getNotFound()
                + "，失败 " + summary.getFailed()
                + "，跳过 " + summary.getSkipped() + "。";
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
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

    private String normalizeStore(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSite(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteCode is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private String shrink(String value, int maxLength) {
        String text = StringUtils.hasText(value) ? value.replaceAll("\\s+", " ").trim() : "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    @FunctionalInterface
    interface TaskSubmitter {
        void submit(String accountKey, Runnable task);
    }
}
