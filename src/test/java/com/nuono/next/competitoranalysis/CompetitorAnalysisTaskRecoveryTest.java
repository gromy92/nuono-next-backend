package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.concurrent.atomic.AtomicBoolean;
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

        assertEquals(1, recovery.resumeQueuedRefreshTasks());
        assertEquals(0, recovery.resumeQueuedRefreshTasks());
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

        assertEquals(0, recovery.resumeQueuedRefreshTasks());

        verify(operationalTaskService, never()).fail(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
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

        assertEquals(0, recovery.resumeQueuedRefreshTasks());

        verify(operationalTaskService, never()).listActiveAfter(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                0L,
                1000
        );
    }

    @Test
    void malformedRetryNotBeforeDoesNotBlockLaterValidQueuedTask() {
        CompetitorRefreshTaskFactory taskFactory =
                org.mockito.Mockito.mock(CompetitorRefreshTaskFactory.class);
        CompetitorDetailRetryCoordinator retryCoordinator =
                new CompetitorDetailRetryCoordinator(
                        taskFactory,
                        Clock.fixed(
                                Instant.parse("2026-06-06T08:00:00Z"),
                                ZoneOffset.UTC
                        )
                );
        OperationalTask malformed = task(
                150001L,
                OperationalTaskStatus.QUEUED,
                "2026-06-06T08:00:00"
        );
        malformed.setPayloadJson(
                "{\"retryAttempt\":1,\"retryNotBefore\":\"not-a-date\"}"
        );
        OperationalTask valid = task(
                150002L,
                OperationalTaskStatus.QUEUED,
                "2026-06-06T08:00:00"
        );
        valid.setPayloadJson(
                "{\"retryAttempt\":1,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-06-06T07:59:00\","
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"SELF\","
                        + "\"noonProductCode\":\"ZSELF001\"}]}"
        );
        CompetitorSearchRunRow malformedRun = run(220001L, "QUEUED");
        CompetitorSearchRunRow validRun = run(220002L, "QUEUED");
        CompetitorWatchProductRow watchProduct = watchProduct();
        AtomicBoolean validSubmitted = new AtomicBoolean(false);
        recovery = new CompetitorAnalysisTaskRecovery(
                mapper,
                operationalTaskService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC),
                (task, run, product) -> {
                    if (!retryCoordinator.isReady(task)) {
                        return false;
                    }
                    if (task.getId().equals(valid.getId())) {
                        validSubmitted.set(true);
                    }
                    return true;
                },
                interruptedTaskRetry
        );
        when(operationalTaskService.listActiveAfter(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                0L,
                1000
        )).thenReturn(List.of(malformed, valid));
        when(operationalTaskService.listActiveAfter(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                150002L,
                1000
        )).thenReturn(List.of());
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(malformedRun);
        when(mapper.selectSearchRunByTaskId(150002L)).thenReturn(validRun);
        when(mapper.selectWatchProductForRefresh(180001L)).thenReturn(watchProduct);

        assertDoesNotThrow(recovery::resumeQueuedRefreshTasks);
        assertTrue(validSubmitted.get());
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

        assertEquals(1, recovery.recoverStaleRefreshTasks());

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

        assertEquals(1, recovery.resumeQueuedRefreshTasks());
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
        return run(220001L, status);
    }

    private static CompetitorSearchRunRow run(long id, String status) {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(id);
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
