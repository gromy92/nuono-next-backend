package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(operationalTaskService.listActive(CompetitorAnalysisRefreshService.TASK_TYPE, 1000))
                .thenReturn(List.of(queued));
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(run);
        when(mapper.selectWatchProductForRefresh(180001L)).thenReturn(watchProduct);
        when(queuedTaskSubmitter.submit(queued, run, watchProduct)).thenReturn(true, false);

        assertEquals(1, recovery.resumeQueuedRefreshTasks());
        assertEquals(0, recovery.resumeQueuedRefreshTasks());
        verify(queuedTaskSubmitter, times(2)).submit(queued, run, watchProduct);
    }

    @Test
    void queuedSnapshotIsIgnoredWhenAnotherProcessAlreadyStartedTheRun() {
        OperationalTask queuedSnapshot = task(OperationalTaskStatus.QUEUED, "2026-06-06T08:00:00");
        CompetitorSearchRunRow runningRun = run("RUNNING");
        when(operationalTaskService.listActive(CompetitorAnalysisRefreshService.TASK_TYPE, 1000))
                .thenReturn(List.of(queuedSnapshot));
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(runningRun);

        assertEquals(0, recovery.resumeQueuedRefreshTasks());

        verify(operationalTaskService, never()).fail(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void staleRunningTaskIsFailedAndRetriedAsANewRun() {
        OperationalTask running = task(OperationalTaskStatus.RUNNING, "2026-06-06T07:20:00");
        CompetitorSearchRunRow run = run("RUNNING");
        CompetitorWatchProductRow watchProduct = watchProduct();
        when(operationalTaskService.listActive(CompetitorAnalysisRefreshService.TASK_TYPE, 1000))
                .thenReturn(List.of(running));
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(run);
        when(mapper.selectWatchProductForRefresh(180001L)).thenReturn(watchProduct);

        assertEquals(1, recovery.recoverStaleRefreshTasks());

        verify(operationalTaskService).fail(150001L, "FAILED_STALE", "刷新任务超过 30 分钟未完成，已自动释放。");
        verify(mapper).markSearchRunFailed(220001L, "FAILED_STALE", "刷新任务超过 30 分钟未完成，已自动释放。");
        verify(interruptedTaskRetry).retry(watchProduct, run);
    }

    private static OperationalTask task(OperationalTaskStatus status, String updatedAt) {
        OperationalTask task = new OperationalTask();
        task.setId(150001L);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.parse(updatedAt));
        return task;
    }

    private static CompetitorSearchRunRow run(String status) {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(220001L);
        run.setWatchProductId(180001L);
        run.setStatus(status);
        run.setTriggerMode("SCHEDULED_RANK_MONITOR");
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
