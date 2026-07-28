package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class ProductListingTaskLease implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ProductListingTaskLease.class);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;
    private static final ScheduledExecutorService HEARTBEAT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

    private final ProductListingMapper mapper;
    private final Long taskId;
    private final Long ownerUserId;
    private final LocalDateTime startedAt;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean lost = new AtomicBoolean(false);
    private final ScheduledFuture<?> heartbeatFuture;

    private ProductListingTaskLease(
            ProductListingMapper mapper,
            Long taskId,
            Long ownerUserId,
            LocalDateTime startedAt
    ) {
        this.mapper = mapper;
        this.taskId = taskId;
        this.ownerUserId = ownerUserId;
        this.startedAt = startedAt;
        heartbeatOrThrow();
        this.heartbeatFuture =
                TransactionSynchronizationManager.isActualTransactionActive()
                        ? null
                        : HEARTBEAT_EXECUTOR.scheduleAtFixedRate(
                                this::heartbeatInBackground,
                                HEARTBEAT_INTERVAL_SECONDS,
                                HEARTBEAT_INTERVAL_SECONDS,
                                TimeUnit.SECONDS
                        );
    }

    static ProductListingTaskLease start(
            ProductListingMapper mapper,
            ProductListingTaskRecord task
    ) {
        if (mapper == null || task == null || task.getId() == null
                || task.getOwnerUserId() == null || task.getStartedAt() == null) {
            throw new IllegalArgumentException("Product listing task lease identity is incomplete.");
        }
        return new ProductListingTaskLease(
                mapper,
                task.getId(),
                task.getOwnerUserId(),
                task.getStartedAt()
        );
    }

    static ProductListingTaskRecord claimRecovery(
            ProductListingMapper mapper,
            ProductListingTaskRecord task
    ) {
        LocalDateTime startedAt = LocalDateTime.now();
        int claimed = mapper.markTaskRecoveryRunning(
                task.getId(),
                task.getOwnerUserId(),
                task.getStatus(),
                startedAt
        );
        if (claimed != 1) {
            throw new IllegalArgumentException(
                    "Product listing task state changed; reload it before retrying recovery."
            );
        }
        ProductListingTaskRecord claimedTask = mapper.selectTaskById(
                task.getId(),
                task.getOwnerUserId()
        );
        if (claimedTask == null || !"running".equals(claimedTask.getStatus())
                || claimedTask.getStartedAt() == null) {
            throw new IllegalStateException("Product listing recovery claim could not be reloaded.");
        }
        return claimedTask;
    }

    void heartbeatOrThrow() {
        if (closed.get() || lost.get()) {
            throw leaseLost();
        }
        final int updated;
        try {
            updated = mapper.heartbeatRunningTask(taskId, ownerUserId, startedAt);
        } catch (RuntimeException exception) {
            lost.set(true);
            throw new IllegalStateException(
                    "Product listing task lease heartbeat failed; external writes are stopped.",
                    exception
            );
        }
        if (updated != 1) {
            lost.set(true);
            throw leaseLost();
        }
    }

    void checkpointNoonResultOrThrow(String noonResultJson) {
        heartbeatOrThrow();
        int updated = mapper.checkpointRunningTaskNoonResult(
                taskId, ownerUserId, noonResultJson, startedAt
        );
        if (updated != 1) {
            lost.set(true);
            throw leaseLost();
        }
    }

    boolean complete(ProductListingTaskRecord task) {
        try {
            heartbeatOrThrow();
        } catch (RuntimeException exception) {
            return false;
        }
        int completed = mapper.completeRunningTaskResult(task, startedAt);
        if (completed != 1) {
            lost.set(true);
            return false;
        }
        return true;
    }

    ProductListingTaskRecord completeOrReload(ProductListingTaskRecord task) {
        if (complete(task)) {
            return task;
        }
        ProductListingTaskRecord current = mapper.selectTaskById(taskId, ownerUserId);
        if (current == null) {
            throw new IllegalStateException("Product listing task disappeared after its lease was lost.");
        }
        return current;
    }

    private void heartbeatInBackground() {
        if (closed.get() || lost.get()) {
            return;
        }
        try {
            heartbeatOrThrow();
        } catch (RuntimeException exception) {
            log.warn(
                    "Product listing task lease lost: taskId={}, ownerUserId={}",
                    taskId,
                    ownerUserId,
                    exception
            );
        }
    }

    private IllegalStateException leaseLost() {
        return new IllegalStateException(
                "Product listing task lease was lost; this worker must not continue Noon writes."
        );
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
            }
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "product-listing-task-heartbeat");
            thread.setDaemon(true);
            return thread;
        }
    }
}
