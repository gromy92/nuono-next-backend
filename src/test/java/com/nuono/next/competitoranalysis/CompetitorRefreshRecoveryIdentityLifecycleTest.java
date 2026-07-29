package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorRefreshRecoveryIdentityLifecycleTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T02:00:00Z"),
            ZoneOffset.UTC
    );
    private static final LocalDateTime STALE_BEFORE =
            LocalDateTime.parse("2026-07-28T01:30:00");
    private static final String ERROR_CODE = "INVALID_REFRESH_RECOVERY_PAYLOAD";
    private static final String ERROR_MESSAGE =
            "陈旧竞品刷新任务的恢复身份或载荷无效，已安全终止。";

    @Mock
    private CompetitorAnalysisMapper mapper;
    @Mock
    private OperationalTaskService taskService;
    @Mock
    private CompetitorTaskSubmitter taskSubmitter;

    private CompetitorRefreshRecoveryCoordinator coordinator;

    @BeforeEach
    void setUp() {
        CompetitorRefreshTaskFactory taskFactory =
                new CompetitorRefreshTaskFactory(mapper, taskService);
        coordinator = new CompetitorRefreshRecoveryCoordinator(
                mapper,
                taskService,
                taskFactory,
                new CompetitorRefreshTaskDispatcher(
                        mapper, taskService, taskSubmitter
                ),
                ignored -> true,
                ignored -> true,
                (taskId, runId, watchProductId, actorUserId, mode) -> {
                },
                CLOCK
        );
    }

    @Test
    void queuedResubmitIdentityMismatchFailsTaskAndRun() {
        OperationalTask task = task(OperationalTaskStatus.QUEUED);
        CompetitorSearchRunRow run = run("QUEUED");
        stubQueuedFailure(task, run);

        assertFalse(coordinator.resubmitQueued(task, run, watchProduct()));

        verifyQueuedFailure(task, run);
        verify(taskSubmitter, never()).submit(any(), any());
    }

    @Test
    void initialDispatchIdentityMismatchFailsTaskAndRun() {
        OperationalTask task = task(OperationalTaskStatus.QUEUED);
        CompetitorSearchRunRow run = run("QUEUED");
        when(taskService.find(task.getId())).thenReturn(Optional.of(task));
        when(mapper.selectSearchRunByTaskId(task.getId())).thenReturn(run);
        stubQueuedFailure(task, run);
        CompetitorQueuedRefresh queued = new CompetitorQueuedRefresh(
                CompetitorRefreshRunView.from(task, run),
                CompetitorMonitoringEnqueueOutcome.CREATED
        );

        coordinator.dispatchQueued(
                queued,
                watchProduct(),
                501L,
                CompetitorRefreshExecutionMode.SCHEDULED_RANK
        );

        verifyQueuedFailure(task, run);
        verify(taskSubmitter, never()).submit(any(), any());
    }

    @Test
    void staleIdentityMismatchFailsOriginalInsteadOfLeavingItActive() {
        OperationalTask task = task(OperationalTaskStatus.RUNNING);
        CompetitorSearchRunRow run = run("RUNNING");
        when(mapper.listActiveKeywordsByWatchProductId(180001L))
                .thenReturn(List.of());
        when(taskService.failStaleRunning(
                task.getId(), STALE_BEFORE, ERROR_CODE, ERROR_MESSAGE
        )).thenReturn(true);
        when(mapper.markActiveSearchRunFailedForTask(
                run.getId(), task.getId(), ERROR_CODE, ERROR_MESSAGE
        )).thenReturn(1);

        assertTrue(coordinator.recoverInterrupted(
                task, watchProduct(), run, STALE_BEFORE
        ));

        verify(taskService).failStaleRunning(
                task.getId(), STALE_BEFORE, ERROR_CODE, ERROR_MESSAGE
        );
        verify(mapper).markActiveSearchRunFailedForTask(
                run.getId(), task.getId(), ERROR_CODE, ERROR_MESSAGE
        );
        verify(taskService, never()).queue(any(), any(), any());
    }

    private void stubQueuedFailure(
            OperationalTask task,
            CompetitorSearchRunRow run
    ) {
        when(mapper.lockQueuedRefreshTask(task.getId())).thenReturn(task.getId());
        when(mapper.lockQueuedRefreshRun(
                task.getId(), run.getId(), run.getWatchProductId()
        )).thenReturn(run.getId());
        when(mapper.failQueuedRefreshRun(
                task.getId(),
                run.getId(),
                run.getWatchProductId(),
                ERROR_CODE,
                ERROR_MESSAGE
        )).thenReturn(1);
    }

    private void verifyQueuedFailure(
            OperationalTask task,
            CompetitorSearchRunRow run
    ) {
        verify(mapper).failQueuedRefreshRun(
                task.getId(),
                run.getId(),
                run.getWatchProductId(),
                ERROR_CODE,
                ERROR_MESSAGE
        );
        verify(taskService).fail(task.getId(), ERROR_CODE, ERROR_MESSAGE);
    }

    private static OperationalTask task(OperationalTaskStatus status) {
        OperationalTask task = new OperationalTask();
        task.setId(150001L);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:999999:rank");
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.parse("2026-07-28T01:20:00"));
        task.setPayloadJson(CompetitorRefreshRecoveryPayload.fresh(
                180001L,
                0,
                CompetitorRefreshExecutionMode.SCHEDULED_RANK,
                null
        ));
        return task;
    }

    private static CompetitorSearchRunRow run(String status) {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(220001L);
        run.setTaskId(150001L);
        run.setWatchProductId(180001L);
        run.setStatus(status);
        run.setTriggerMode("SCHEDULED_RANK_MONITOR");
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
