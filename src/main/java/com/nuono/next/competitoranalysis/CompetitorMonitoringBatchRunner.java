package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.server.ResponseStatusException;

final class CompetitorMonitoringBatchRunner {
    static final int SCOPE_PAGE_SIZE = 100;
    static final int PRODUCT_PAGE_SIZE = 500;
    static final String RUNNING_MESSAGE = "竞品监控批次正在持久化待执行任务。";

    private static final Logger log = LoggerFactory.getLogger(CompetitorMonitoringBatchRunner.class);

    private final CompetitorMonitoringMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final ProductEnqueuer productEnqueuer;
    private final Runnable childTaskPump;

    CompetitorMonitoringBatchRunner(
            CompetitorMonitoringMapper mapper,
            OperationalTaskService operationalTaskService,
            ProductEnqueuer productEnqueuer,
            Runnable childTaskPump
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.productEnqueuer = productEnqueuer;
        this.childTaskPump = childTaskPump;
    }

    int run(OperationalTask task) {
        if (task == null || !operationalTaskService.claimQueued(task.getId(), RUNNING_MESSAGE)) {
            return 0;
        }
        CompetitorMonitoringCheckpoint checkpoint;
        try {
            checkpoint = CompetitorMonitoringCheckpoint.fromJson(task.getPayloadJson());
        } catch (RuntimeException exception) {
            safeFail(task.getId(), exception);
            log.warn("invalid competitor monitoring checkpoint taskId={}", task.getId(), exception);
            return 0;
        }
        try {
            if ("CYCLE".equals(checkpoint.getBatchKind())) {
                runCycle(task.getId(), checkpoint);
            } else {
                runStore(task.getId(), checkpoint);
            }
            checkpoint.setCompleted(true);
            operationalTaskService.complete(task.getId(), checkpoint.toJson(), completionMessage(checkpoint));
            childTaskPump.run();
            return (int) checkpoint.getCompletedScopeCount();
        } catch (LeaseLostException exception) {
            return 0;
        } catch (DataAccessException exception) {
            log.warn(
                    "competitor monitoring batch parked for lease recovery taskId={} batchKey={} error={}",
                    task.getId(),
                    checkpoint.getBatchKey(),
                    exception.getMessage()
            );
            return 0;
        } catch (RuntimeException exception) {
            safeFail(task.getId(), exception);
            log.warn(
                    "competitor monitoring batch failed taskId={} batchKey={} error={}",
                    task.getId(),
                    checkpoint.getBatchKey(),
                    exception.getMessage(),
                    exception
            );
            return 0;
        }
    }

    private void runCycle(Long taskId, CompetitorMonitoringCheckpoint checkpoint) {
        if (checkpoint.getCurrentOwnerUserId() != null) {
            drainCurrentScope(taskId, checkpoint);
            finishCurrentScope(taskId, checkpoint);
        }
        while (true) {
            List<CompetitorWatchProductScopeRow> scopes = mapper.listRefreshableWatchProductScopes(
                    checkpoint.getUpperWatchProductId(),
                    checkpoint.getAfterScopeOwnerUserId(),
                    checkpoint.getAfterScopeStoreCode(),
                    checkpoint.getAfterScopeSiteCode(),
                    checkpoint.getUpperScopeOwnerUserId(),
                    checkpoint.getUpperScopeStoreCode(),
                    checkpoint.getUpperScopeSiteCode(),
                    SCOPE_PAGE_SIZE
            );
            if (scopes.isEmpty()) {
                return;
            }
            for (CompetitorWatchProductScopeRow scope : scopes) {
                checkpoint.setCurrentOwnerUserId(scope.getOwnerUserId());
                checkpoint.setCurrentStoreCode(scope.getStoreCode());
                checkpoint.setCurrentSiteCode(scope.getSiteCode());
                checkpoint.setAfterWatchProductId(0L);
                save(taskId, checkpoint);
                drainCurrentScope(taskId, checkpoint);
                finishCurrentScope(taskId, checkpoint);
            }
        }
    }

    private void runStore(Long taskId, CompetitorMonitoringCheckpoint checkpoint) {
        drainCurrentScope(taskId, checkpoint);
    }

    private void drainCurrentScope(Long taskId, CompetitorMonitoringCheckpoint checkpoint) {
        CompetitorRefreshExecutionMode mode = CompetitorRefreshExecutionMode.fromTriggerMode(
                checkpoint.getTriggerMode()
        );
        while (true) {
            List<CompetitorWatchProductRow> products = mapper.listRefreshableWatchProducts(
                    checkpoint.getCurrentOwnerUserId(),
                    checkpoint.getCurrentStoreCode(),
                    checkpoint.getCurrentSiteCode(),
                    checkpoint.getAfterWatchProductId(),
                    checkpoint.getUpperWatchProductId(),
                    PRODUCT_PAGE_SIZE
            );
            if (products.isEmpty()) {
                return;
            }
            for (CompetitorWatchProductRow product : products) {
                try {
                    CompetitorMonitoringEnqueueOutcome outcome = productEnqueuer.enqueue(
                            product,
                            checkpoint.getRequestedBy(),
                            mode,
                            checkpoint.getBatchKey()
                    );
                    checkpoint.record(outcome);
                } catch (ResponseStatusException exception) {
                    if (!"COMPETITOR_NO_ACTIVE_KEYWORD".equals(exception.getReason())) {
                        throw exception;
                    }
                    checkpoint.recordFailure();
                    log.warn(
                            "competitor monitoring enqueue failed taskId={} watchProductId={} error={}",
                            taskId,
                            product.getId(),
                            exception.getMessage()
                    );
                }
                checkpoint.setAfterWatchProductId(product.getId());
                save(taskId, checkpoint);
            }
        }
    }

    private void finishCurrentScope(Long taskId, CompetitorMonitoringCheckpoint checkpoint) {
        checkpoint.setAfterScopeOwnerUserId(checkpoint.getCurrentOwnerUserId());
        checkpoint.setAfterScopeStoreCode(checkpoint.getCurrentStoreCode());
        checkpoint.setAfterScopeSiteCode(checkpoint.getCurrentSiteCode());
        checkpoint.setCurrentOwnerUserId(null);
        checkpoint.setCurrentStoreCode(null);
        checkpoint.setCurrentSiteCode(null);
        checkpoint.setAfterWatchProductId(0L);
        checkpoint.setCompletedScopeCount(checkpoint.getCompletedScopeCount() + 1L);
        save(taskId, checkpoint);
    }

    private void save(Long taskId, CompetitorMonitoringCheckpoint checkpoint) {
        if (!operationalTaskService.checkpointRunning(
                taskId,
                checkpoint.toJson(),
                checkpoint.progressPercent(),
                RUNNING_MESSAGE
        )) {
            throw new LeaseLostException();
        }
    }

    private void safeFail(Long taskId, RuntimeException exception) {
        try {
            operationalTaskService.fail(
                    taskId,
                    "COMPETITOR_MONITOR_FAILED",
                    exception.getMessage() == null ? "竞品监控批次失败。" : exception.getMessage()
            );
        } catch (IllegalStateException ignored) {
            // Another recovery worker already closed this lease.
        }
    }

    private String completionMessage(CompetitorMonitoringCheckpoint checkpoint) {
        return checkpoint.getFailed() > 0L
                ? "竞品监控批次已完整枚举，部分商品入队失败。"
                : checkpoint.getDeferred() > 0L
                ? "竞品监控批次已完整枚举，部分商品由已有刷新任务承接。"
                : "竞品监控批次已提交。";
    }

    @FunctionalInterface
    interface ProductEnqueuer {
        CompetitorMonitoringEnqueueOutcome enqueue(
                CompetitorWatchProductRow product,
                Long requestedBy,
                CompetitorRefreshExecutionMode mode,
                String batchKey
        );
    }

    private static final class LeaseLostException extends RuntimeException {
    }
}
