package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class CompetitorRefreshLeaseConcurrencyTest {
    @Test
    void recoveryWaitsForOwnedBoundaryThenOldWorkerCannotReacquire()
            throws Exception {
        Long taskId = 150124L;
        Long runId = 220124L;
        Long watchId = 180123L;
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        ReentrantLock taskRowLock = new ReentrantLock();
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch leaseAcquired = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch replacementCommitted = new CountDownLatch(1);
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        AtomicReference<Throwable> recoveryFailure = new AtomicReference<>();

        when(mapper.lockRunningRefreshTask(taskId)).thenAnswer(ignored -> {
            taskRowLock.lock();
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            taskRowLock.unlock();
                        }
                    }
            );
            return running.get() ? taskId : null;
        });
        when(mapper.heartbeatRunningRefreshTask(
                org.mockito.ArgumentMatchers.eq(taskId), any()
        )).thenAnswer(ignored -> running.get() ? 1 : 0);
        when(mapper.lockRunningRefreshRun(taskId, runId, watchId))
                .thenAnswer(ignored -> running.get() ? runId : null);
        CompetitorRefreshLeaseGuard guard =
                new CompetitorRefreshLeaseGuard(
                        mapper, Clock.systemUTC(), true
                );

        Thread worker = new Thread(() -> {
            beginTransaction();
            try {
                guard.acquire(taskId, runId, watchId);
                leaseAcquired.countDown();
                assertTrue(releaseWorker.await(2, TimeUnit.SECONDS));
            } catch (Throwable failure) {
                workerFailure.set(failure);
            } finally {
                completeTransaction();
            }
        });
        Thread recovery = new Thread(() -> {
            try {
                assertTrue(leaseAcquired.await(2, TimeUnit.SECONDS));
                taskRowLock.lock();
                try {
                    running.set(false);
                    replacementCommitted.countDown();
                } finally {
                    taskRowLock.unlock();
                }
            } catch (Throwable failure) {
                recoveryFailure.set(failure);
            }
        });

        worker.start();
        recovery.start();
        assertTrue(leaseAcquired.await(2, TimeUnit.SECONDS));
        assertFalse(replacementCommitted.await(100, TimeUnit.MILLISECONDS));
        releaseWorker.countDown();
        worker.join(2000);
        recovery.join(2000);
        assertTrue(workerFailure.get() == null);
        assertTrue(recoveryFailure.get() == null);
        assertTrue(replacementCommitted.getCount() == 0);

        beginTransaction();
        try {
            assertThrows(
                    CompetitorRefreshLeaseLostException.class,
                    () -> guard.acquire(taskId, runId, watchId)
            );
        } finally {
            completeTransaction();
        }
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void completeTransaction() {
        TransactionSynchronizationUtils.triggerAfterCompletion(
                TransactionSynchronization.STATUS_COMMITTED
        );
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
}
