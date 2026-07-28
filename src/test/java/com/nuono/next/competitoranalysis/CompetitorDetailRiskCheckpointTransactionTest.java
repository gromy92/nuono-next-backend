package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CompetitorDetailRiskCheckpointTransactionTest {
    private static final Long TASK_ID = 150001L;
    private static final Long RUN_ID = 220001L;
    private static final Long WATCH_PRODUCT_ID = 180001L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T02:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void riskHoldIsPersistedBeforeFailureCheckpointInsideFencedTransaction() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            CompetitorAnalysisMapper mapper =
                    context.getBean(CompetitorAnalysisMapper.class);
            OperationalTaskService taskService =
                    context.getBean(OperationalTaskService.class);
            CompetitorRefreshExecutionFinalizer finalizer =
                    context.getBean(CompetitorRefreshExecutionFinalizer.class);
            CountingTransactionManager transactions =
                    context.getBean(CountingTransactionManager.class);
            List<String> writes = new ArrayList<>();
            stubLease(mapper);
            when(taskService.checkpointRunning(
                    eq(TASK_ID), any(String.class), eq(5), any(String.class)
            )).thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager
                        .isActualTransactionActive());
                writes.add("checkpoint");
                return true;
            });
            NoonRiskBackoffHold expectedHold = riskHold();
            CompetitorProductDetailTarget target =
                    CompetitorProductDetailTarget.self("ZSELF001");
            CompetitorDetailRetrySession session = openSession(
                    mapper,
                    taskService,
                    finalizer,
                    task(),
                    target,
                    (errorCode, errorMessage) -> {
                        assertTrue(TransactionSynchronizationManager
                                .isActualTransactionActive());
                        assertEquals("RATE_LIMITED", errorCode);
                        assertEquals("HTTP 429", errorMessage);
                        writes.add("hold");
                        return expectedHold;
                    }
            );
            writes.clear();
            session.beginRequest(target);
            writes.clear();

            session.recordFailure(
                    target, "RATE_LIMITED", "HTTP 429", true
            );

            assertEquals(List.of("hold", "checkpoint"), writes);
            assertSame(
                    expectedHold,
                    session.ensureRiskHold("RATE_LIMITED", "HTTP 429")
            );
            assertEquals(3, transactions.commitCount);
            assertEquals(0, transactions.rollbackCount);
            assertTrue(AopUtils.isAopProxy(finalizer));
        }
    }

    @Test
    void checkpointFenceLossRollsBackRiskTransactionAndKeepsReservation() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            CompetitorAnalysisMapper mapper =
                    context.getBean(CompetitorAnalysisMapper.class);
            OperationalTaskService taskService =
                    context.getBean(OperationalTaskService.class);
            CompetitorRefreshExecutionFinalizer finalizer =
                    context.getBean(CompetitorRefreshExecutionFinalizer.class);
            CountingTransactionManager transactions =
                    context.getBean(CountingTransactionManager.class);
            AtomicInteger checkpoints = new AtomicInteger();
            List<String> writes = new ArrayList<>();
            stubLease(mapper);
            when(taskService.checkpointRunning(
                    eq(TASK_ID), any(String.class), eq(5), any(String.class)
            )).thenAnswer(invocation -> {
                writes.add("checkpoint");
                return checkpoints.incrementAndGet() < 3;
            });
            OperationalTask task = task();
            CompetitorProductDetailTarget target =
                    CompetitorProductDetailTarget.self("ZSELF001");
            CompetitorDetailRetrySession session = openSession(
                    mapper,
                    taskService,
                    finalizer,
                    task,
                    target,
                    (errorCode, errorMessage) -> {
                        assertTrue(TransactionSynchronizationManager
                                .isActualTransactionActive());
                        writes.add("hold");
                        return riskHold();
                    }
            );
            writes.clear();
            session.beginRequest(target);
            String reservedPayload = task.getPayloadJson();
            writes.clear();

            assertThrows(
                    CompetitorRefreshLeaseLostException.class,
                    () -> session.recordFailure(
                            target, "CAPTCHA_REQUIRED", "captcha", true
                    )
            );

            assertEquals(List.of("hold", "checkpoint"), writes);
            assertEquals(reservedPayload, task.getPayloadJson());
            assertEquals(2, transactions.commitCount);
            assertEquals(1, transactions.rollbackCount);
        }
    }

    private static CompetitorDetailRetrySession openSession(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService taskService,
            CompetitorRefreshExecutionFinalizer finalizer,
            OperationalTask task,
            CompetitorProductDetailTarget target,
            BiFunction<String, String, NoonRiskBackoffHold> riskRecorder
    ) {
        CompetitorRefreshTaskFactory factory =
                new CompetitorRefreshTaskFactory(
                        mapper, taskService, finalizer
                );
        return new CompetitorDetailRetryCoordinator(factory, CLOCK).openSession(
                task,
                RUN_ID,
                WATCH_PRODUCT_ID,
                List.of(target),
                riskRecorder
        );
    }

    private static void stubLease(CompetitorAnalysisMapper mapper) {
        when(mapper.lockRunningRefreshTask(TASK_ID)).thenReturn(TASK_ID);
        when(mapper.heartbeatRunningRefreshTask(
                eq(TASK_ID), any(LocalDateTime.class)
        )).thenReturn(1);
        when(mapper.lockRunningRefreshRun(
                TASK_ID, RUN_ID, WATCH_PRODUCT_ID
        )).thenReturn(RUN_ID);
    }

    private static OperationalTask task() {
        OperationalTask task = new OperationalTask();
        task.setId(TASK_ID);
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setPayloadJson(CompetitorRefreshRecoveryPayload.fresh(
                WATCH_PRODUCT_ID,
                0,
                CompetitorRefreshExecutionMode.SCHEDULED_DETAIL,
                "detail:2026-07-28"
        ));
        return task;
    }

    private static NoonRiskBackoffHold riskHold() {
        NoonRiskBackoffHold hold = new NoonRiskBackoffHold();
        hold.setRiskType("rate_limited");
        hold.setSourceTaskId(TASK_ID);
        hold.setBlockedUntil(LocalDateTime.parse("2026-07-28T02:02:00"));
        return hold;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        CompetitorAnalysisMapper mapper() {
            return mock(CompetitorAnalysisMapper.class);
        }

        @Bean
        OperationalTaskService taskService() {
            return mock(OperationalTaskService.class);
        }

        @Bean
        CompetitorRefreshLeaseGuard leaseGuard(
                CompetitorAnalysisMapper mapper
        ) {
            return new CompetitorRefreshLeaseGuard(mapper);
        }

        @Bean
        CompetitorRefreshExecutionFinalizer finalizer(
                CompetitorAnalysisMapper mapper,
                OperationalTaskService taskService,
                CompetitorRefreshLeaseGuard leaseGuard
        ) {
            return new CompetitorRefreshExecutionFinalizer(
                    mapper, taskService, leaseGuard
            );
        }

        @Bean
        CountingTransactionManager transactionManager() {
            return new CountingTransactionManager();
        }
    }

    static final class CountingTransactionManager
            extends AbstractPlatformTransactionManager {
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition
        ) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }
    }
}
