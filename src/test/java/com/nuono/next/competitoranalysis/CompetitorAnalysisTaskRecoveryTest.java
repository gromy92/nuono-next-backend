package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorAnalysisTaskRecoveryTest {
    @Mock
    private CompetitorAnalysisMapper mapper;
    @Mock
    private OperationalTaskService operationalTaskService;
    @Mock
    private CompetitorAnalysisTaskRecovery.QueuedTaskSubmitter queuedTaskSubmitter;
    @Mock
    private CompetitorAnalysisTaskRecovery.InterruptedTaskRetry interruptedTaskRetry;
    @Mock
    private CompetitorRefreshExecutionFinalizer executionFinalizer;

    private CompetitorAnalysisTaskRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new CompetitorAnalysisTaskRecovery(
                mapper,
                operationalTaskService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC),
                queuedTaskSubmitter,
                interruptedTaskRetry
        );
    }

    @Test
    void queuedTaskIsResubmittedFromPersistedTaskAndRun() {
        OperationalTask queued = task(OperationalTaskStatus.QUEUED, "2026-06-06T08:00:00");
        CompetitorSearchRunRow run = run("QUEUED");
        CompetitorWatchProductRow watchProduct = watchProduct();
        when(operationalTaskService.listActiveAfter(CompetitorAnalysisRefreshService.TASK_TYPE, 0L, 1000))
                .thenReturn(List.of(queued));
        when(operationalTaskService.listActiveAfter(CompetitorAnalysisRefreshService.TASK_TYPE, 150001L, 1000))
                .thenReturn(List.of());
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(run);
        when(mapper.selectWatchProductForRefresh(180001L)).thenReturn(watchProduct);
        when(queuedTaskSubmitter.submit(queued, run, watchProduct)).thenReturn(true, false);

        assertEquals(1, recovery.resumeQueuedManualRefreshTasks());
        assertEquals(0, recovery.resumeQueuedManualRefreshTasks());
        verify(queuedTaskSubmitter, times(2)).submit(queued, run, watchProduct);
    }

    @Test
    void queuedSnapshotIsIgnoredWhenAnotherProcessAlreadyStartedTheRun() {
        OperationalTask queuedSnapshot = task(OperationalTaskStatus.QUEUED, "2026-06-06T08:00:00");
        CompetitorSearchRunRow runningRun = run("RUNNING");
        when(operationalTaskService.listActiveAfter(CompetitorAnalysisRefreshService.TASK_TYPE, 0L, 1000))
                .thenReturn(List.of(queuedSnapshot));
        when(operationalTaskService.listActiveAfter(CompetitorAnalysisRefreshService.TASK_TYPE, 150001L, 1000))
                .thenReturn(List.of());
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(runningRun);

        assertEquals(0, recovery.resumeQueuedManualRefreshTasks());

        verify(operationalTaskService, never()).fail(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void missingQueuedWatchUsesAtomicRunAndTaskFinalizer() {
        OperationalTask queued = task(OperationalTaskStatus.QUEUED, "2026-06-06T08:00:00");
        CompetitorSearchRunRow run = run("QUEUED");
        recovery = new CompetitorAnalysisTaskRecovery(
                mapper,
                operationalTaskService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC),
                queuedTaskSubmitter,
                interruptedTaskRetry,
                executionFinalizer,
                () -> 1000
        );
        when(operationalTaskService.listActiveAfter(
                CompetitorAnalysisRefreshService.TASK_TYPE, 0L, 1000
        )).thenReturn(List.of(queued));
        when(operationalTaskService.listActiveAfter(
                CompetitorAnalysisRefreshService.TASK_TYPE, 150001L, 1000
        )).thenReturn(List.of());
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(run);
        when(mapper.selectWatchProductForRefresh(180001L)).thenReturn(null);

        assertEquals(0, recovery.resumeQueuedManualRefreshTasks());

        verify(executionFinalizer).failQueued(
                150001L,
                220001L,
                180001L,
                "COMPETITOR_WATCH_PRODUCT_NOT_FOUND",
                "监控商品不存在或已删除。"
        );
        verify(operationalTaskService, never()).fail(
                anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void queuedRecoveryDoesNotGrowTheInMemoryDispatcherPastItsCapacity() {
        recovery = new CompetitorAnalysisTaskRecovery(
                mapper,
                operationalTaskService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC),
                queuedTaskSubmitter,
                interruptedTaskRetry,
                () -> 0
        );

        assertEquals(0, recovery.resumeQueuedManualRefreshTasks());

        verify(operationalTaskService, never()).listActiveAfter(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                0L,
                1000
        );
    }

    @Test
    void staleRunningTaskIsFailedAndRetriedAsANewRun() {
        OperationalTask running = task(OperationalTaskStatus.RUNNING, "2026-06-06T07:20:00");
        CompetitorSearchRunRow run = run("RUNNING");
        CompetitorWatchProductRow watchProduct = watchProduct();
        when(operationalTaskService.listActiveAfter(CompetitorAnalysisRefreshService.TASK_TYPE, 0L, 1000))
                .thenReturn(List.of(running));
        when(operationalTaskService.listActiveAfter(CompetitorAnalysisRefreshService.TASK_TYPE, 150001L, 1000))
                .thenReturn(List.of());
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(run);
        when(mapper.selectWatchProductForRefresh(180001L)).thenReturn(watchProduct);
        when(interruptedTaskRetry.retry(
                running,
                watchProduct,
                run,
                LocalDateTime.parse("2026-06-06T07:30:00")
        )).thenReturn(true);

        assertEquals(1, recovery.recoverStaleManualRefreshTasks());

        verify(interruptedTaskRetry).retry(
                running,
                watchProduct,
                run,
                LocalDateTime.parse("2026-06-06T07:30:00")
        );
    }

    @Test
    void saturatedQueuedAccountPrefixDoesNotHideALaterAccount() {
        recovery = new CompetitorAnalysisTaskRecovery(
                mapper,
                operationalTaskService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC),
                queuedTaskSubmitter,
                interruptedTaskRetry,
                () -> 1
        );
        List<OperationalTask> firstPage = LongStream.rangeClosed(1L, 1000L)
                .mapToObj(id -> task(id, OperationalTaskStatus.QUEUED, "2026-06-06T08:00:00"))
                .collect(java.util.stream.Collectors.toList());
        OperationalTask queued = task(1001L, OperationalTaskStatus.QUEUED, "2026-06-06T08:00:00");
        CompetitorSearchRunRow run = run("QUEUED");
        CompetitorWatchProductRow watchProduct = watchProduct();
        when(operationalTaskService.listActiveAfter(CompetitorAnalysisRefreshService.TASK_TYPE, 0L, 1000))
                .thenReturn(firstPage);
        when(operationalTaskService.listActiveAfter(CompetitorAnalysisRefreshService.TASK_TYPE, 1000L, 1000))
                .thenReturn(List.of(queued));
        when(mapper.selectSearchRunByTaskId(anyLong())).thenReturn(run);
        when(mapper.selectWatchProductForRefresh(180001L)).thenReturn(watchProduct);
        when(queuedTaskSubmitter.submit(any(), eq(run), eq(watchProduct)))
                .thenAnswer(invocation -> ((OperationalTask) invocation.getArgument(0)).getId() == 1001L);

        assertEquals(1, recovery.resumeQueuedManualRefreshTasks());
        verify(queuedTaskSubmitter).submit(queued, run, watchProduct);
    }

    private static OperationalTask task(OperationalTaskStatus status, String updatedAt) {
        return task(150001L, status, updatedAt);
    }

    private static OperationalTask task(long id, OperationalTaskStatus status, String updatedAt) {
        OperationalTask task = new OperationalTask();
        task.setId(id);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.parse(updatedAt));
        return task;
    }

    private static CompetitorSearchRunRow run(String status) {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(220001L);
        run.setWatchProductId(180001L);
        run.setStatus(status);
        run.setTriggerMode("MANUAL_REFRESH");
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
