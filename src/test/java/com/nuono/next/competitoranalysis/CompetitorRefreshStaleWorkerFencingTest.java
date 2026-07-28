package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorRefreshExecutionMapper;
import com.nuono.next.system.task.OperationalTaskService;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class CompetitorRefreshStaleWorkerFencingTest {
    private static final Long TASK_ID = 150124L;
    private static final Long RUN_ID = 220124L;
    private static final Long WATCH_ID = 180123L;

    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private OperationalTaskService taskService;
    @Mock private CompetitorKeywordRefreshRunner keywordRunner;
    @Mock private NoonFrontendSearchAdapter searchAdapter;
    private CompetitorRefreshLeaseGuard leaseGuard;

    @BeforeEach
    void setUp() {
        leaseGuard = new CompetitorRefreshLeaseGuard(
                mapper,
                Clock.fixed(
                        Instant.parse("2026-07-28T08:00:00Z"),
                        ZoneOffset.UTC
                ),
                true
        );
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void progressAndTerminalWritesStopWhenRecoveryOwnsTask() {
        CompetitorRefreshExecutionFinalizer finalizer =
                new CompetitorRefreshExecutionFinalizer(
                        mapper, taskService, leaseGuard
                );

        assertThrows(
                CompetitorRefreshLeaseLostException.class,
                () -> finalizer.progress(
                        TASK_ID, RUN_ID, WATCH_ID, 35, "running"
                )
        );
        assertThrows(
                CompetitorRefreshLeaseLostException.class,
                () -> finalizer.fail(
                        TASK_ID, RUN_ID, WATCH_ID,
                        "REFRESH_FAILED", "failed", 501L
                )
        );

        verify(taskService, never()).progress(any(), any(), any());
        verify(taskService, never()).fail(any(), any(), any());
        verify(mapper, never()).failRunningRefreshRun(
                any(), any(), any(), any(), any(), any()
        );
        verify(mapper, never()).updateLatestRefreshRunIfNotOlder(
                any(), any(), any(), any()
        );
    }

    @Test
    void progressUsesTaskHeartbeatRunLockOrder() {
        runningLease();
        CompetitorRefreshExecutionFinalizer finalizer =
                new CompetitorRefreshExecutionFinalizer(
                        mapper, taskService, leaseGuard
                );

        finalizer.progress(TASK_ID, RUN_ID, WATCH_ID, 35, "running");

        InOrder order = inOrder(mapper, taskService);
        order.verify(mapper).lockRunningRefreshTask(TASK_ID);
        order.verify(mapper).heartbeatRunningRefreshTask(
                org.mockito.ArgumentMatchers.eq(TASK_ID), any()
        );
        order.verify(mapper).lockRunningRefreshRun(
                TASK_ID, RUN_ID, WATCH_ID
        );
        order.verify(taskService).progress(TASK_ID, 35, "running");
    }

    @Test
    void keywordSuccessAndFailureEvidenceAreFencedAfterProviderReturns() {
        runningLease();
        when(mapper.nextKeywordRunId()).thenReturn(230001L, 230002L);
        when(keywordRunner.refresh(any()))
                .thenReturn(CompetitorKeywordRefreshOutcome.success(0))
                .thenThrow(new IllegalStateException("provider failed"));
        CompetitorKeywordRefreshTransactionRunner runner =
                new CompetitorKeywordRefreshTransactionRunner(
                        mapper, keywordRunner, leaseGuard
                );
        CompetitorWatchProductRow watch = watch();
        CompetitorKeywordRow keyword = keyword();

        runner.runKeyword(TASK_ID, RUN_ID, watch, keyword, 501L);
        runner.runKeyword(TASK_ID, RUN_ID, watch, keyword, 501L);

        InOrder order = inOrder(keywordRunner, mapper);
        order.verify(keywordRunner).refresh(any());
        verifyLease(order);
        order.verify(mapper).insertKeywordRun(any());
        order.verify(mapper).markKeywordProviderSucceeded(
                keyword.getId(), "SUCCESS", 501L
        );
        order.verify(keywordRunner).refresh(any());
        verifyLease(order);
        order.verify(mapper).insertKeywordRun(any());
        order.verify(mapper).markKeywordProviderFailed(
                keyword.getId(),
                "COMPETITOR_PROVIDER_FAILED",
                "provider failed",
                501L
        );
    }

    @Test
    void searchResultWritesAcquireLeaseAfterAdapterReturns() {
        runningLease();
        when(searchAdapter.search(any())).thenReturn(new NoonSearchPage());
        CompetitorSearchRefreshRunner runner =
                new CompetitorSearchRefreshRunner(
                        mapper, searchAdapter, leaseGuard
                );

        runner.refresh(CompetitorKeywordRefreshContext.builder()
                .taskId(TASK_ID)
                .searchRunId(RUN_ID)
                .keywordRunId(230001L)
                .watchProduct(watch())
                .keyword(keyword())
                .actorUserId(501L)
                .executionLeaseRequired(true)
                .build());

        InOrder order = inOrder(searchAdapter, mapper);
        order.verify(searchAdapter).search(any());
        verifyLease(order);
        order.verify(mapper).softDeleteDiscoveredKeywordProductRelationsOutsideSet(
                any(), any(), any()
        );
    }

    @Test
    void executionSqlUsesStrictIdentityCasAndMonotonicLatestRun()
            throws Exception {
        String taskLock = selectSql("lockRunningRefreshTask", Long.class);
        String runLock = selectSql(
                "lockRunningRefreshRun",
                Long.class,
                Long.class,
                Long.class
        );
        assertTrue(taskLock.contains("STATUS = 'RUNNING'"));
        assertTrue(taskLock.contains("FOR UPDATE"));
        assertTrue(runLock.contains("TASK_ID = #{TASKID}"));
        assertTrue(runLock.contains("WATCH_PRODUCT_ID = #{WATCHPRODUCTID}"));
        assertTrue(runLock.contains("STATUS = 'RUNNING'"));
        assertTrue(runLock.contains("FOR UPDATE"));

        for (String sql : java.util.List.of(
                updateSql(
                        "completeRunningRefreshRun",
                        Long.class, Long.class, Long.class, String.class,
                        int.class, int.class, int.class, int.class,
                        String.class, String.class, Long.class
                ),
                updateSql(
                        "requeueRunningRefreshRun",
                        Long.class,
                        Long.class,
                        Long.class,
                        String.class,
                        String.class
                ),
                updateSql(
                        "failRunningRefreshRun",
                        Long.class,
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        Long.class
                )
        )) {
            assertTrue(sql.contains("TASK_ID = #{TASKID}"));
            assertTrue(sql.contains("WATCH_PRODUCT_ID = #{WATCHPRODUCTID}"));
            assertTrue(sql.contains("STATUS = 'RUNNING'"));
        }
        assertTrue(updateSql(
                "updateLatestRefreshRunIfNotOlder",
                Long.class,
                Long.class,
                String.class,
                Long.class
        ).contains("LATEST_RUN_ID <= #{RUNID}"));
    }

    private static String selectSql(String name, Class<?>... types)
            throws Exception {
        Method method = CompetitorRefreshExecutionMapper.class
                .getMethod(name, types);
        return normalize(String.join(
                " ", method.getAnnotation(Select.class).value()
        ));
    }

    private static String updateSql(String name, Class<?>... types)
            throws Exception {
        Method method = CompetitorRefreshExecutionMapper.class
                .getMethod(name, types);
        return normalize(String.join(
                " ", method.getAnnotation(Update.class).value()
        ));
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private void runningLease() {
        when(mapper.lockRunningRefreshTask(TASK_ID)).thenReturn(TASK_ID);
        when(mapper.heartbeatRunningRefreshTask(
                org.mockito.ArgumentMatchers.eq(TASK_ID), any()
        )).thenReturn(1);
        when(mapper.lockRunningRefreshRun(
                TASK_ID, RUN_ID, WATCH_ID
        )).thenReturn(RUN_ID);
    }

    private void verifyLease(InOrder order) {
        order.verify(mapper).lockRunningRefreshTask(TASK_ID);
        order.verify(mapper).heartbeatRunningRefreshTask(
                org.mockito.ArgumentMatchers.eq(TASK_ID), any()
        );
        order.verify(mapper).lockRunningRefreshRun(
                TASK_ID, RUN_ID, WATCH_ID
        );
    }

    private static CompetitorWatchProductRow watch() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(WATCH_ID);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode("ZSELF001");
        return row;
    }

    private static CompetitorKeywordRow keyword() {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(190001L);
        row.setWatchProductId(WATCH_ID);
        row.setKeyword("laundry basket");
        row.setLocale("en-SA");
        return row;
    }
}
