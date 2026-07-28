package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

@ExtendWith(MockitoExtension.class)
class CompetitorRefreshTaskFactoryRecoveryTest {
    private static final LocalDateTime STALE_BEFORE = LocalDateTime.parse("2026-06-06T07:30:00");
    private static final String STALE_MESSAGE = "刷新任务超过 30 分钟未完成，已自动释放。";

    @Mock
    private CompetitorAnalysisMapper mapper;
    @Mock
    private OperationalTaskService operationalTaskService;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void renewedTaskCasMissLeavesRunAndReplacementUntouched() {
        CompetitorRefreshTaskFactory factory = factory();
        OperationalTask staleSnapshot = staleTask();
        CompetitorSearchRunRow staleRun = staleRun();
        when(operationalTaskService.failStaleRunning(
                staleSnapshot.getId(), STALE_BEFORE, "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(false);
        AtomicBoolean dispatched = new AtomicBoolean(false);

        CompetitorQueuedRefresh replacement = factory.replaceStale(
                staleSnapshot,
                staleRun,
                watchProduct(),
                STALE_BEFORE,
                staleRun.getRequestedBy(),
                CompetitorRefreshExecutionMode.SCHEDULED_DETAIL,
                "batch-20260606",
                0,
                ignored -> dispatched.set(true)
        );

        assertNull(replacement);
        assertFalse(dispatched.get());
        verify(mapper, never()).markActiveSearchRunFailedForTask(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(operationalTaskService, never()).queue(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                any()
        );
    }

    @Test
    void linkedRunCasMissAbortsBeforeReplacementPersistence() {
        CompetitorRefreshTaskFactory factory = factory();
        OperationalTask staleSnapshot = staleTask();
        CompetitorSearchRunRow staleRun = staleRun();
        when(operationalTaskService.failStaleRunning(
                staleSnapshot.getId(), STALE_BEFORE, "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(true);
        when(mapper.markActiveSearchRunFailedForTask(
                staleRun.getId(), staleSnapshot.getId(), "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> factory.replaceStale(
                        staleSnapshot,
                        staleRun,
                        watchProduct(),
                        STALE_BEFORE,
                        staleRun.getRequestedBy(),
                        CompetitorRefreshExecutionMode.SCHEDULED_DETAIL,
                        "batch-20260606",
                        0,
                        ignored -> {
                        }
                )
        );

        verify(operationalTaskService, never()).queue(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                any()
        );
    }

    @Test
    void replacementPersistenceFailureNeverDispatchesAndUsesOneTransactionBoundary() throws Exception {
        CompetitorRefreshTaskFactory factory = factory();
        OperationalTask staleSnapshot = staleTask();
        CompetitorSearchRunRow staleRun = staleRun();
        when(operationalTaskService.failStaleRunning(
                staleSnapshot.getId(), STALE_BEFORE, "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(true);
        when(mapper.markActiveSearchRunFailedForTask(
                staleRun.getId(), staleSnapshot.getId(), "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(1);
        when(operationalTaskService.queue(any(), any(), any()))
                .thenThrow(new IllegalStateException("replacement insert failed"));
        AtomicBoolean dispatched = new AtomicBoolean(false);

        assertThrows(
                IllegalStateException.class,
                () -> factory.replaceStale(
                        staleSnapshot,
                        staleRun,
                        watchProduct(),
                        STALE_BEFORE,
                        staleRun.getRequestedBy(),
                        CompetitorRefreshExecutionMode.SCHEDULED_DETAIL,
                        "batch-20260606",
                        0,
                        ignored -> dispatched.set(true)
                )
        );

        assertFalse(dispatched.get());
        Method method = CompetitorRefreshTaskFactory.class.getMethod(
                "replaceStale",
                OperationalTask.class,
                CompetitorSearchRunRow.class,
                CompetitorWatchProductRow.class,
                LocalDateTime.class,
                Long.class,
                CompetitorRefreshExecutionMode.class,
                String.class,
                int.class,
                java.util.function.Consumer.class
        );
        assertTrue(method.isAnnotationPresent(Transactional.class));
        InOrder order = inOrder(operationalTaskService, mapper);
        order.verify(operationalTaskService).failStaleRunning(
                staleSnapshot.getId(), STALE_BEFORE, "FAILED_STALE", STALE_MESSAGE
        );
        order.verify(mapper).markActiveSearchRunFailedForTask(
                staleRun.getId(), staleSnapshot.getId(), "FAILED_STALE", STALE_MESSAGE
        );
        order.verify(operationalTaskService).queue(any(), any(), any());
    }

    @Test
    void replacementDispatchWaitsUntilTransactionCommit() {
        CompetitorRefreshTaskFactory factory = factory();
        OperationalTask staleSnapshot = staleTask();
        CompetitorSearchRunRow staleRun = staleRun();
        OperationalTask replacementTask = replacementTask();
        when(operationalTaskService.failStaleRunning(
                staleSnapshot.getId(), STALE_BEFORE, "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(true);
        when(mapper.markActiveSearchRunFailedForTask(
                staleRun.getId(), staleSnapshot.getId(), "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(1);
        when(operationalTaskService.queue(any(), any(), any())).thenReturn(replacementTask);
        when(mapper.nextSearchRunId()).thenReturn(220002L);
        AtomicBoolean dispatched = new AtomicBoolean(false);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        CompetitorQueuedRefresh replacement = factory.replaceStale(
                staleSnapshot,
                staleRun,
                watchProduct(),
                STALE_BEFORE,
                staleRun.getRequestedBy(),
                CompetitorRefreshExecutionMode.SCHEDULED_DETAIL,
                "batch-20260606",
                0,
                ignored -> dispatched.set(true)
        );

        assertFalse(dispatched.get());
        TransactionSynchronizationUtils.triggerAfterCommit();
        assertTrue(dispatched.get());
        assertTrue(replacement.getView().getTaskId().equals(replacementTask.getId()));
    }

    @Test
    void mapperRunFailureIsBoundToActiveRunAndOriginalTask() throws Exception {
        Method method = CompetitorAnalysisMapper.class.getMethod(
                "markActiveSearchRunFailedForTask",
                Long.class,
                Long.class,
                String.class,
                String.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("task_id = #{taskId}"));
        assertTrue(sql.contains("status IN ('QUEUED', 'RUNNING')"));
        assertTrue(sql.contains("id = #{runId}"));
    }

    private CompetitorRefreshTaskFactory factory() {
        return new CompetitorRefreshTaskFactory(mapper, operationalTaskService);
    }

    private static OperationalTask staleTask() {
        OperationalTask task = new OperationalTask();
        task.setId(150001L);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:180001:detail");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        return task;
    }

    private static OperationalTask replacementTask() {
        OperationalTask task = staleTask();
        task.setId(150002L);
        task.setStatus(OperationalTaskStatus.QUEUED);
        task.setPayloadJson("{\"batchKey\":\"batch-20260606\"}");
        return task;
    }

    private static CompetitorSearchRunRow staleRun() {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(220001L);
        run.setTaskId(150001L);
        run.setWatchProductId(180001L);
        run.setStatus("RUNNING");
        run.setTriggerMode("SCHEDULED_DETAIL_MONITOR");
        run.setRequestedBy(501L);
        return run;
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180001L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        return row;
    }
}
