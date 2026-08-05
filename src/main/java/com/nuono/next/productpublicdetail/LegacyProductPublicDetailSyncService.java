package com.nuono.next.productpublicdetail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailRequest;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Compatibility executor used only while the predecessor DP-05 scheduler owns automatic pulls. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
public class LegacyProductPublicDetailSyncService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final ProductPublicDetailMapper mapper;
    private final OperationalTaskService tasks;
    private final ObjectProvider<NoonAccountTaskQueue> queueProvider;
    private final ProductPublicDetailSyncService facts;
    private final ObjectMapper objectMapper;
    private final int maxProductsPerTask;
    private final int staleDays;
    private final int failureCooldownHours;

    public LegacyProductPublicDetailSyncService(
            ProductPublicDetailMapper mapper,
            OperationalTaskService tasks,
            ObjectProvider<NoonAccountTaskQueue> queueProvider,
            ProductPublicDetailSyncService facts,
            ObjectMapper objectMapper,
            @Value("${nuono.product-public-detail.scheduler.max-products-per-task:100}")
            int maxProductsPerTask,
            @Value("${nuono.product-public-detail.scheduler.stale-days:1}") int staleDays,
            @Value("${nuono.product-public-detail.scheduler.failure-cooldown-hours:12}")
            int failureCooldownHours
    ) {
        this.mapper = mapper;
        this.tasks = tasks;
        this.queueProvider = queueProvider;
        this.facts = facts;
        this.objectMapper = objectMapper;
        this.maxProductsPerTask = Integer.MAX_VALUE;
        this.staleDays = Math.max(1, staleDays);
        this.failureCooldownHours = 0;
    }

    public ProductPublicDetailTaskView submitScheduled(
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        String store = facts.normalizeStore(storeCode);
        String site = facts.normalizeSite(siteCode);
        OperationalTask active = tasks.listActive(ProductPublicDetailSyncService.TASK_TYPE, 1000)
                .stream()
                .filter(task -> sameScope(task, ownerUserId, store, site))
                .findFirst()
                .orElse(null);
        if (active != null) return ProductPublicDetailTaskView.from(active);
        OperationalTask task = tasks.start(
                ProductPublicDetailSyncService.TASK_TYPE,
                "product-public-detail:" + ownerUserId + ":" + store + ":" + site
                        + ":" + LocalDate.now(BUSINESS_ZONE),
                OperationalTaskPayload.builder()
                        .ownerUserId(ownerUserId)
                        .storeCode(store)
                        .siteCode(site)
                        .payloadJson(toJson(Map.of(
                                "ownerUserId", ownerUserId,
                                "storeCode", store,
                                "siteCode", site,
                                "trigger", "SCHEDULED"
                        )))
                        .message("商品前台详情同步正在后台执行。")
                        .build()
        );
        Runnable execution = () -> runTask(task.getId(), ownerUserId, store, site);
        NoonAccountTaskQueue queue = queueProvider.getIfAvailable();
        if (queue == null) execution.run();
        else queue.submit(ownerUserId + ":" + store, execution);
        return ProductPublicDetailTaskView.from(task);
    }

    void runTask(Long taskId, Long ownerUserId, String store, String site) {
        long startedNanos = System.nanoTime();
        ProductPublicDetailSyncSummary summary = new ProductPublicDetailSyncSummary();
        NoonPublicProductDetailAdapter adapter = facts.adapter();
        if (adapter == null) {
            tasks.fail(taskId, "PRODUCT_PUBLIC_DETAIL_ADAPTER_UNAVAILABLE", "Noon 前台公开详情 adapter 不可用。");
            return;
        }
        Optional<NoonRiskBackoffHold> activeHold = facts.currentRiskBackoffHold(
                ownerUserId, store, site);
        if (activeHold.isPresent()) {
            failRiskBackoff(taskId, activeHold.get(), summary);
            return;
        }
        summary.setAdapterVersion(adapter.adapterVersion());
        try {
            List<ProductPublicDetailCandidate> candidates = mapper.listCandidates(
                    ownerUserId, store, site, maxProductsPerTask, staleDays,
                    failureCooldownHours, true, true);
            summary.setSelected(candidates.size());
            int index = 0;
            for (ProductPublicDetailCandidate candidate : candidates) {
                Optional<NoonRiskBackoffHold> currentHold = facts.currentRiskBackoffHold(
                        ownerUserId, store, site);
                if (currentHold.isPresent()) {
                    failRiskBackoff(taskId, currentHold.get(), summary);
                    return;
                }
                index++;
                String code = NoonProductCodeSupport.normalize(candidate.getNoonProductCode());
                if (!StringUtils.hasText(code)
                        || NoonProductCodeSupport.codeType(code).isEmpty()) {
                    summary.setSkipped(summary.getSkipped() + 1);
                    continue;
                }
                ProductPublicDetailSnapshot snapshot;
                try {
                    NoonPublicProductDetailResult result = adapter.fetch(
                            NoonPublicProductDetailRequest.builder()
                                    .siteCode(candidate.getSiteCode())
                                    .locale(facts.defaultLocale(candidate.getSiteCode()))
                                    .noonProductCode(code)
                                    .build());
                    if (result == null) {
                        result = facts.failureResult(
                                code, "PROVIDER_EMPTY_RESPONSE",
                                "Noon 前台公开详情 adapter 返回空结果。",
                                null, null, null, null);
                    }
                    snapshot = facts.toSnapshot(candidate, result, ownerUserId);
                } catch (Exception failure) {
                    snapshot = facts.toSnapshot(candidate, facts.failureResult(
                            code, "PROVIDER_EXCEPTION", facts.shrink(failure.getMessage(), 300),
                            null, null, null, null), ownerUserId);
                }
                facts.upsertSnapshot(snapshot);
                summary.increment(snapshot.getSyncStatus());
                Optional<NoonRiskBackoffHold> newHold = facts.recordRiskBackoffIfNeeded(
                        taskId, ownerUserId, store, site, snapshot);
                if (newHold.isPresent()) {
                    failRiskBackoff(taskId, newHold.get(), summary);
                    return;
                }
                tasks.progress(taskId, progress(index, candidates.size()), progress(summary, index));
            }
            summary.setElapsedMillis(elapsedMillis(startedNanos));
            tasks.complete(taskId, toJson(summary), complete(summary));
        } catch (Exception failure) {
            tasks.fail(taskId, "PRODUCT_PUBLIC_DETAIL_SYNC_FAILED",
                    facts.shrink(failure.getMessage(), 500));
        }
    }

    private void failRiskBackoff(
            Long taskId,
            NoonRiskBackoffHold hold,
            ProductPublicDetailSyncSummary summary
    ) {
        tasks.fail(taskId, "PRODUCT_PUBLIC_DETAIL_RISK_BACKOFF",
                "商品前台详情触发 Noon 风控退避：" + hold.getRiskType()
                        + "，冷却至 " + hold.getBlockedUntil()
                        + "；本轮已处理 " + summary.getSelected() + " 个候选中的部分商品。");
    }

    private boolean sameScope(
            OperationalTask task,
            Long ownerUserId,
            String store,
            String site
    ) {
        return task != null
                && Objects.equals(task.getOwnerUserId(), ownerUserId)
                && Objects.equals(facts.normalizeStore(task.getStoreCode()), store)
                && Objects.equals(facts.normalizeSite(task.getSiteCode()), site);
    }

    private int progress(int index, int total) {
        if (total <= 0) return 100;
        return Math.max(1, Math.min(99, (int) Math.floor(index * 100.0 / total)));
    }

    private String progress(ProductPublicDetailSyncSummary summary, int index) {
        return "商品前台详情同步中：" + index + "/" + summary.getSelected()
                + "，成功 " + summary.getSucceeded() + "，部分 " + summary.getPartial()
                + "，未找到 " + summary.getNotFound() + "，失败 " + summary.getFailed()
                + "，跳过 " + summary.getSkipped() + "。";
    }

    private String complete(ProductPublicDetailSyncSummary summary) {
        return "商品前台详情同步完成：成功 " + summary.getSucceeded()
                + "，部分 " + summary.getPartial() + "，未找到 " + summary.getNotFound()
                + "，失败 " + summary.getFailed() + "，跳过 " + summary.getSkipped() + "。";
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            return "{}";
        }
    }
}
