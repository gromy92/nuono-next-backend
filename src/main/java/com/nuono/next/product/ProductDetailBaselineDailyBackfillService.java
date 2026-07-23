package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductDetailBaselineCandidateMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductDetailBaselineDailyBackfillService {
    private static final Logger log = LoggerFactory.getLogger(ProductDetailBaselineDailyBackfillService.class);
    private static final String REASON = "daily-maintenance-audit";
    private static final String STALE_RECOVERY_MESSAGE = "每日详情基线巡检回收超时任务，允许后续重试。";

    private final ProductDetailBaselineCandidateMapper candidateMapper;
    private final ProductDetailBaselineBackfillService backfillService;
    private final LocalDbProductMasterService productMasterService;
    private final OperationalTaskService operationalTaskService;
    private final boolean enabled;
    private final int staleAfterMinutes;
    private final Clock clock;

    @Autowired
    public ProductDetailBaselineDailyBackfillService(
            ProductDetailBaselineCandidateMapper candidateMapper,
            ProductDetailBaselineBackfillService backfillService,
            LocalDbProductMasterService productMasterService,
            OperationalTaskService operationalTaskService,
            @Value("${nuono.product-management.detail-baseline-daily-backfill.enabled:false}") boolean enabled,
            @Value("${nuono.product-management.detail-baseline-daily-backfill.stale-after-minutes:360}")
            int staleAfterMinutes
    ) {
        this(
                candidateMapper,
                backfillService,
                productMasterService,
                operationalTaskService,
                enabled,
                staleAfterMinutes,
                Clock.systemUTC()
        );
    }

    ProductDetailBaselineDailyBackfillService(
            ProductDetailBaselineCandidateMapper candidateMapper,
            ProductDetailBaselineBackfillService backfillService,
            LocalDbProductMasterService productMasterService,
            OperationalTaskService operationalTaskService,
            boolean enabled,
            int staleAfterMinutes,
            Clock clock
    ) {
        this.candidateMapper = candidateMapper;
        this.backfillService = backfillService;
        this.productMasterService = productMasterService;
        this.operationalTaskService = operationalTaskService;
        this.enabled = enabled;
        this.staleAfterMinutes = Math.max(1, staleAfterMinutes);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public EnqueueResult enqueueMissingAfterDailyList(Long ownerUserId, String storeCode, String siteCode) {
        if (!enabled) {
            return EnqueueResult.disabled();
        }
        if (ownerUserId == null || !StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)) {
            return EnqueueResult.enabled(0, 0, 0, 0);
        }

        int recovered = recoverStaleTasks(ownerUserId, storeCode);
        List<ProductDetailBaselineCandidate> candidates = candidateMapper.listMissingMaintainedCandidates(
                ownerUserId,
                storeCode.trim(),
                siteCode.trim().toUpperCase(Locale.ROOT)
        );
        Set<String> scheduledProducts = new HashSet<>();
        int enqueued = 0;
        int failed = 0;
        for (ProductDetailBaselineCandidate candidate : safeCandidates(candidates)) {
            String productKey = productKey(candidate);
            if (!StringUtils.hasText(productKey) || !scheduledProducts.add(productKey)) {
                continue;
            }
            try {
                String state = backfillService.enqueue(
                        toFetchCommand(ownerUserId, storeCode, candidate),
                        REASON,
                        (command, ignoredReason) -> productMasterService.fetchSnapshot(command)
                );
                if ("preparing".equals(state)) {
                    enqueued++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn(
                        "daily product detail baseline enqueue failed owner={} store={} skuParent={} error={}",
                        ownerUserId,
                        storeCode,
                        candidate == null ? null : candidate.getSkuParent(),
                        exception.getMessage(),
                        exception
                );
            }
        }
        EnqueueResult result = EnqueueResult.enabled(candidates == null ? 0 : candidates.size(), enqueued, failed, recovered);
        log.info(
                "daily product detail baseline audit owner={} store={} site={} candidates={} enqueued={} failed={} staleRecovered={}",
                ownerUserId,
                storeCode,
                siteCode,
                result.getCandidateCount(),
                result.getEnqueuedCount(),
                result.getFailedCount(),
                result.getStaleRecoveredCount()
        );
        return result;
    }

    private int recoverStaleTasks(Long ownerUserId, String storeCode) {
        LocalDateTime staleBefore = LocalDateTime.now(clock).minusMinutes(staleAfterMinutes);
        int recovered = 0;
        for (OperationalTask task : operationalTaskService.listActive(ProductDetailBaselineBackfillService.TASK_TYPE, 1000)) {
            if (task == null
                    || !ownerUserId.equals(task.getOwnerUserId())
                    || !equalsIgnoreCase(storeCode, task.getStoreCode())
                    || task.getUpdatedAt() == null
                    || !task.getUpdatedAt().isBefore(staleBefore)) {
                continue;
            }
            try {
                operationalTaskService.cancel(task.getId(), STALE_RECOVERY_MESSAGE);
                recovered++;
            } catch (IllegalStateException exception) {
                log.debug("detail baseline task {} finished while stale recovery was running", task.getId());
            }
        }
        return recovered;
    }

    private ProductMasterFetchCommand toFetchCommand(
            Long ownerUserId,
            String storeCode,
            ProductDetailBaselineCandidate candidate
    ) {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(ownerUserId);
        command.setStoreCode(storeCode.trim());
        command.setSkuParent(candidate.getSkuParent());
        command.setCurrentZCode(candidate.getSkuParent());
        command.setPartnerSku(candidate.getPartnerSku());
        command.setPskuCode(candidate.getPskuCode());
        return command;
    }

    private String productKey(ProductDetailBaselineCandidate candidate) {
        if (candidate == null || !StringUtils.hasText(candidate.getSkuParent())) {
            return null;
        }
        return candidate.getLogicalStoreId() + "::" + candidate.getSkuParent().trim();
    }

    private List<ProductDetailBaselineCandidate> safeCandidates(List<ProductDetailBaselineCandidate> candidates) {
        return candidates == null ? List.of() : candidates;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.trim().equalsIgnoreCase(right.trim());
    }

    public static final class EnqueueResult {
        private final boolean enabled;
        private final int candidateCount;
        private final int enqueuedCount;
        private final int failedCount;
        private final int staleRecoveredCount;

        private EnqueueResult(
                boolean enabled,
                int candidateCount,
                int enqueuedCount,
                int failedCount,
                int staleRecoveredCount
        ) {
            this.enabled = enabled;
            this.candidateCount = candidateCount;
            this.enqueuedCount = enqueuedCount;
            this.failedCount = failedCount;
            this.staleRecoveredCount = staleRecoveredCount;
        }

        static EnqueueResult disabled() {
            return new EnqueueResult(false, 0, 0, 0, 0);
        }

        static EnqueueResult enabled(int candidates, int enqueued, int failed, int recovered) {
            return new EnqueueResult(true, candidates, enqueued, failed, recovered);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getCandidateCount() {
            return candidateCount;
        }

        public int getEnqueuedCount() {
            return enqueuedCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public int getStaleRecoveredCount() {
            return staleRecoveredCount;
        }
    }
}
