package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductActiveStateReconciliationMapper;
import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductActiveStateReconciliationService {
    static final String TASK_TYPE = ProductActiveStateReconciliationMapper.TASK_TYPE;
    private static final String QUEUED_MESSAGE = "商品在售状态已进入 Noon 权威核实队列。";
    private static final String STALE_MESSAGE = "核实任务执行超时，已自动回收并允许后续续跑。";
    private static final Logger log = LoggerFactory.getLogger(ProductActiveStateReconciliationService.class);

    private final ProductActiveStateReconciliationMapper mapper;
    private final OperationalTaskService taskService;
    private final ProductActiveStateReconciliationGuard guard;
    private final ProductActiveStateReconciliationBatchRunner batchRunner;
    private final TaskSubmitter taskSubmitter;
    private final int maxItemsPerScope;
    private final int staleAfterMinutes;
    private final Clock clock;

    @Autowired
    public ProductActiveStateReconciliationService(
            ProductActiveStateReconciliationMapper mapper,
            OperationalTaskService taskService,
            LocalDbProductMasterService productMasterService,
            NoonAccountTaskQueue taskQueue,
            NoonRiskBackoffGuard riskBackoffGuard,
            NoonPullFailurePolicy failurePolicy,
            @Value("${nuono.product-management.active-state-reconciliation.max-items-per-scope:10}")
            int maxItemsPerScope,
            @Value("${nuono.product-management.active-state-reconciliation.stale-after-minutes:60}")
            int staleAfterMinutes
    ) {
        this(
                mapper,
                taskService,
                productMasterService,
                taskQueue == null ? null : taskQueue::submit,
                new ProductActiveStateReconciliationGuard(riskBackoffGuard, failurePolicy),
                maxItemsPerScope,
                staleAfterMinutes,
                Clock.systemUTC()
        );
    }

    ProductActiveStateReconciliationService(
            ProductActiveStateReconciliationMapper mapper,
            OperationalTaskService taskService,
            LocalDbProductMasterService productMasterService,
            TaskSubmitter taskSubmitter,
            ProductActiveStateReconciliationGuard guard,
            int maxItemsPerScope,
            int staleAfterMinutes,
            Clock clock
    ) {
        this.mapper = mapper;
        this.taskService = taskService;
        this.taskSubmitter = taskSubmitter == null ? (accountKey, task) -> task.run() : taskSubmitter;
        this.guard = guard == null ? ProductActiveStateReconciliationGuard.disabled(clock) : guard;
        this.maxItemsPerScope = Math.max(1, maxItemsPerScope);
        this.staleAfterMinutes = Math.max(1, staleAfterMinutes);
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.batchRunner = new ProductActiveStateReconciliationBatchRunner(
                mapper,
                taskService,
                productMasterService,
                this.guard,
                clock
        );
    }

    public int enqueueUnknownScopes(int maxScopes) {
        recoverStaleTasks();
        List<ProductActiveStateReconciliationScope> scopes =
                mapper.listUnknownScopes(Math.max(1, maxScopes));
        int queued = 0;
        for (ProductActiveStateReconciliationScope scope : safeScopes(scopes)) {
            queued += enqueueScope(
                    scope.getOwnerUserId(),
                    scope.getStoreCode(),
                    scope.getSiteCode(),
                    scope.getUnknownCount()
            ).getQueuedCount();
        }
        return queued;
    }

    private void recoverStaleTasks() {
        LocalDateTime staleBefore = LocalDateTime.now(clock).minusMinutes(staleAfterMinutes);
        for (OperationalTask task : taskService.listActive(TASK_TYPE, 1000)) {
            if (task == null
                    || task.getId() == null
                    || task.getUpdatedAt() == null
                    || !task.getUpdatedAt().isBefore(staleBefore)) {
                continue;
            }
            try {
                taskService.cancel(task.getId(), STALE_MESSAGE);
            } catch (IllegalStateException ignored) {
                log.debug("active-state task {} finished during stale recovery", task.getId());
            }
        }
    }

    public ProductActiveStateReconciliationEnqueueResult enqueueScope(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            int knownUnknownCount
    ) {
        String normalizedStore = normalize(storeCode);
        String normalizedSite = upper(siteCode);
        if (ownerUserId == null || !StringUtils.hasText(normalizedStore) || !StringUtils.hasText(normalizedSite)) {
            return new ProductActiveStateReconciliationEnqueueResult(knownUnknownCount, 0, false);
        }
        if (guard.isHeld(ownerUserId, normalizedStore, normalizedSite)) {
            return new ProductActiveStateReconciliationEnqueueResult(knownUnknownCount, 0, true);
        }

        List<ProductActiveStateReconciliationCandidate> candidates = mapper.listUnknownCandidates(
                ownerUserId,
                normalizedStore,
                normalizedSite,
                maxItemsPerScope
        );
        List<ProductActiveStateReconciliationBatchRunner.WorkItem> workItems = new ArrayList<>();
        for (ProductActiveStateReconciliationCandidate candidate : safeCandidates(candidates)) {
            String naturalKey = naturalKey(candidate);
            if (!StringUtils.hasText(naturalKey)
                    || taskService.findActive(TASK_TYPE, naturalKey).isPresent()) {
                continue;
            }
            OperationalTask task = taskService.queue(
                    TASK_TYPE,
                    naturalKey,
                    OperationalTaskPayload.builder()
                            .ownerUserId(candidate.getOwnerUserId())
                            .storeCode(candidate.getStoreCode())
                            .siteCode(candidate.getSiteCode())
                            .payloadJson(payload(candidate))
                            .message(QUEUED_MESSAGE)
                            .build()
            );
            if (task.getStatus() == OperationalTaskStatus.QUEUED) {
                workItems.add(new ProductActiveStateReconciliationBatchRunner.WorkItem(
                        task.getId(),
                        candidate
                ));
            }
        }
        if (!workItems.isEmpty()) {
            taskSubmitter.submit(accountKey(ownerUserId), () -> batchRunner.run(workItems));
        }
        log.info(
                "product active-state reconciliation owner={} store={} site={} unknown={} queued={}",
                ownerUserId,
                normalizedStore,
                normalizedSite,
                knownUnknownCount,
                workItems.size()
        );
        return new ProductActiveStateReconciliationEnqueueResult(
                knownUnknownCount,
                workItems.size(),
                false
        );
    }

    static String naturalKey(ProductActiveStateReconciliationCandidate candidate) {
        if (candidate == null || candidate.getOwnerUserId() == null || candidate.getSiteOfferId() == null) {
            return null;
        }
        return "owner:" + candidate.getOwnerUserId() + "|siteOffer:" + candidate.getSiteOfferId();
    }

    private String payload(ProductActiveStateReconciliationCandidate candidate) {
        return "{\"siteOfferId\":" + candidate.getSiteOfferId()
                + ",\"siteCode\":\"" + escape(candidate.getSiteCode())
                + "\",\"partnerSku\":\"" + escape(candidate.getPartnerSku()) + "\"}";
    }

    private String accountKey(Long ownerUserId) {
        return "product-active-state::owner:" + ownerUserId;
    }

    private List<ProductActiveStateReconciliationScope> safeScopes(
            List<ProductActiveStateReconciliationScope> scopes
    ) {
        return scopes == null ? List.of() : scopes;
    }

    private List<ProductActiveStateReconciliationCandidate> safeCandidates(
            List<ProductActiveStateReconciliationCandidate> candidates
    ) {
        return candidates == null ? List.of() : candidates;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @FunctionalInterface
    interface TaskSubmitter {
        void submit(String accountKey, Runnable task);
    }
}
