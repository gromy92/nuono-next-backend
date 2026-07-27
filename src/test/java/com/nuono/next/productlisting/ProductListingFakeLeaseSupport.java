package com.nuono.next.productlisting;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

final class ProductListingFakeLeaseSupport {
    private final Map<Long, ProductListingTaskRecord> tasks;
    private final Map<Long, LocalDateTime> activeLeases = new LinkedHashMap<>();

    ProductListingFakeLeaseSupport(Map<Long, ProductListingTaskRecord> tasks) {
        this.tasks = tasks;
    }

    int recoverStale(LocalDateTime staleBefore) {
        int recovered = 0;
        for (ProductListingTaskRecord task : tasks.values()) {
            if (!"REAL_RUN".equals(task.getMode())
                    || !"running".equals(task.getStatus())
                    || task.getGmtUpdated() == null
                    || !task.getGmtUpdated().isBefore(staleBefore)) {
                continue;
            }
            task.setStatus("written_verify_failed");
            task.setFailureCategory("recovery");
            task.setFailureCode("real_run_interrupted");
            task.setFailureMessage("真实上架任务执行中断，需人工核对。");
            task.setCompletedAt(LocalDateTime.now());
            activeLeases.remove(task.getId());
            recovered++;
        }
        return recovered;
    }

    int markRunning(Long taskId, LocalDateTime startedAt) {
        ProductListingTaskRecord task = tasks.get(taskId);
        if (task == null || !"REAL_RUN".equals(task.getMode())
                || !"submitted".equals(task.getStatus())) {
            return 0;
        }
        activate(task, startedAt);
        return 1;
    }

    int markRecovery(
            Long taskId,
            Long ownerUserId,
            String expectedStatus,
            LocalDateTime startedAt
    ) {
        ProductListingTaskRecord task = tasks.get(taskId);
        if (task == null || !ownerUserId.equals(task.getOwnerUserId())
                || !expectedStatus.equals(task.getStatus())) {
            return 0;
        }
        task.setCompletedAt(null);
        activate(task, startedAt);
        return 1;
    }

    int heartbeat(Long taskId, Long ownerUserId, LocalDateTime startedAt) {
        ProductListingTaskRecord task = tasks.get(taskId);
        if (task == null || !ownerUserId.equals(task.getOwnerUserId())
                || !startedAt.equals(activeLeases.get(taskId))) {
            return 0;
        }
        task.setGmtUpdated(LocalDateTime.now());
        return 1;
    }

    int checkpoint(Long taskId, Long ownerUserId, String noonResultJson, LocalDateTime startedAt) {
        ProductListingTaskRecord task = tasks.get(taskId);
        if (task == null || !ownerUserId.equals(task.getOwnerUserId())
                || !startedAt.equals(activeLeases.get(taskId))) {
            return 0;
        }
        task.setNoonResultJson(noonResultJson);
        task.setGmtUpdated(LocalDateTime.now());
        return 1;
    }

    int complete(ProductListingTaskRecord task, LocalDateTime expectedStartedAt) {
        if (task == null || !expectedStartedAt.equals(activeLeases.get(task.getId()))) {
            return 0;
        }
        release(task.getId());
        tasks.put(task.getId(), task);
        return 1;
    }

    void forceRunning(Long taskId, LocalDateTime startedAt) {
        ProductListingTaskRecord task = requireTask(taskId);
        activate(task, startedAt);
        task.setGmtUpdated(startedAt);
    }

    void forceLoss(Long taskId) {
        ProductListingTaskRecord task = requireTask(taskId);
        release(taskId);
        task.setStatus("written_verify_failed");
        task.setFailureCategory("recovery");
        task.setFailureCode("real_run_interrupted");
        task.setFailureMessage("真实上架任务执行中断，需人工核对。");
        task.setCompletedAt(LocalDateTime.now());
        task.setGmtUpdated(LocalDateTime.now());
    }

    void release(Long taskId) {
        activeLeases.remove(taskId);
    }

    private void activate(ProductListingTaskRecord task, LocalDateTime startedAt) {
        task.setStatus("running");
        task.setStartedAt(startedAt);
        task.setGmtUpdated(LocalDateTime.now());
        activeLeases.put(task.getId(), startedAt);
        tasks.put(task.getId(), task);
    }

    private ProductListingTaskRecord requireTask(Long taskId) {
        ProductListingTaskRecord task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        return task;
    }
}
