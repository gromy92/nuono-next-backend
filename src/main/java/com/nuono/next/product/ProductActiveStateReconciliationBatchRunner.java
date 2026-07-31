package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductActiveStateReconciliationMapper;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

final class ProductActiveStateReconciliationBatchRunner {
    private static final String RUNNING_MESSAGE = "正在按店铺、站点和 PSKU 核实 Noon 在售状态。";
    private static final String HELD_MESSAGE = "Noon 触发限流或风控保护，本次核实已安全停止，解除后将自动续跑。";
    private static final Logger log =
            LoggerFactory.getLogger(ProductActiveStateReconciliationBatchRunner.class);

    private final ProductActiveStateReconciliationMapper mapper;
    private final OperationalTaskService taskService;
    private final LocalDbProductMasterService productMasterService;
    private final ProductActiveStateReconciliationGuard guard;
    private final Clock clock;

    ProductActiveStateReconciliationBatchRunner(
            ProductActiveStateReconciliationMapper mapper,
            OperationalTaskService taskService,
            LocalDbProductMasterService productMasterService,
            ProductActiveStateReconciliationGuard guard,
            Clock clock
    ) {
        this.mapper = mapper;
        this.taskService = taskService;
        this.productMasterService = productMasterService;
        this.guard = guard;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    void run(List<WorkItem> workItems) {
        for (int index = 0; index < workItems.size(); index++) {
            WorkItem workItem = workItems.get(index);
            ProductActiveStateReconciliationCandidate candidate = workItem.candidate;
            if (guard.isHeld(candidate.getOwnerUserId(), candidate.getStoreCode(), candidate.getSiteCode())) {
                cancelRemaining(workItems, index);
                break;
            }
            if (!taskService.claimQueued(workItem.taskId, RUNNING_MESSAGE)) {
                continue;
            }
            try {
                runOne(workItem);
            } catch (RuntimeException exception) {
                safeFail(workItem.taskId, exception);
                logFailure(candidate, exception);
                if (guard.isHeld(candidate.getOwnerUserId(), candidate.getStoreCode(), candidate.getSiteCode())) {
                    cancelRemaining(workItems, index + 1);
                    break;
                }
            }
        }
    }

    private void runOne(WorkItem workItem) {
        ProductActiveStateReconciliationCandidate candidate = workItem.candidate;
        taskService.progress(workItem.taskId, 20, RUNNING_MESSAGE);
        ProductMasterSnapshotView snapshot = guard.fetch(
                productMasterService,
                command(candidate),
                candidate.getSiteCode()
        );
        Optional<Boolean> resolved = ProductActiveStateEvidenceResolver.resolve(snapshot, candidate);
        if (resolved.isEmpty()) {
            taskService.fail(
                    workItem.taskId,
                    "ACTIVE_STATE_EVIDENCE_MISSING",
                    "Noon 定价接口未返回当前店铺、站点和 PSKU 的明确在售状态；该报价仍保留在自动核实队列。"
            );
            return;
        }
        LocalDateTime syncedAt = LocalDateTime.now(clock);
        int changed = mapper.resolveUnknownActiveState(
                candidate.getSiteOfferId(),
                candidate.getOwnerUserId(),
                candidate.getStoreCode(),
                candidate.getSiteCode(),
                candidate.getPartnerSku(),
                resolved.get(),
                ProductActiveStateEvidenceResolver.TRUSTED_SOURCE,
                syncedAt
        );
        if (changed != 1) {
            taskService.fail(
                    workItem.taskId,
                    "ACTIVE_STATE_SCOPE_CHANGED",
                    "核实时目标报价已变化，系统未写入跨范围数据；后续巡检会按最新范围重新判断。"
            );
            return;
        }
        taskService.complete(
                workItem.taskId,
                result(candidate, resolved.get(), syncedAt),
                Boolean.TRUE.equals(resolved.get())
                        ? "已由 Noon 权威定价接口确认商品在售。"
                        : "已由 Noon 权威定价接口确认商品停用。"
        );
    }

    private ProductMasterFetchCommand command(ProductActiveStateReconciliationCandidate candidate) {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(candidate.getOwnerUserId());
        command.setStoreCode(candidate.getStoreCode());
        command.setSkuParent(candidate.getSkuParent());
        command.setCurrentZCode(candidate.getSkuParent());
        command.setPartnerSku(candidate.getPartnerSku());
        command.setPskuCode(candidate.getPskuCode());
        return command;
    }

    private void cancelRemaining(List<WorkItem> workItems, int startIndex) {
        for (int index = startIndex; index < workItems.size(); index++) {
            try {
                taskService.cancel(workItems.get(index).taskId, HELD_MESSAGE);
            } catch (IllegalStateException ignored) {
                log.debug("active-state task {} already became terminal", workItems.get(index).taskId);
            }
        }
    }

    private void safeFail(Long taskId, RuntimeException exception) {
        try {
            taskService.fail(
                    taskId,
                    "ACTIVE_STATE_RECONCILIATION_FAILED",
                    firstNonBlank(exception.getMessage(), "商品在售状态核实失败，系统会在后续批次自动重试。")
            );
        } catch (IllegalStateException ignored) {
            log.debug("active-state task {} already became terminal", taskId);
        }
    }

    private void logFailure(
            ProductActiveStateReconciliationCandidate candidate,
            RuntimeException exception
    ) {
        log.warn(
                "product active-state reconciliation failed owner={} store={} site={} partnerSku={} error={}",
                candidate.getOwnerUserId(),
                candidate.getStoreCode(),
                candidate.getSiteCode(),
                candidate.getPartnerSku(),
                exception.getMessage(),
                exception
        );
    }

    private String result(
            ProductActiveStateReconciliationCandidate candidate,
            boolean active,
            LocalDateTime syncedAt
    ) {
        return "{\"siteOfferId\":" + candidate.getSiteOfferId()
                + ",\"partnerSku\":\"" + escape(candidate.getPartnerSku())
                + "\",\"isActive\":" + active
                + ",\"source\":\"" + ProductActiveStateEvidenceResolver.TRUSTED_SOURCE
                + "\",\"syncedAt\":\"" + syncedAt + "\"}";
    }

    private String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static final class WorkItem {
        private final Long taskId;
        private final ProductActiveStateReconciliationCandidate candidate;

        WorkItem(Long taskId, ProductActiveStateReconciliationCandidate candidate) {
            this.taskId = taskId;
            this.candidate = candidate;
        }
    }
}
