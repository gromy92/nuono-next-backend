package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorDetailBatchTakeoverTest {
    private static final long WATCH_ID = 180123L;
    private static final long CURRENT_TASK_ID = 150300L;
    private static final long CURRENT_RUN_ID = 220300L;

    @Mock private CompetitorAnalysisMapper mapper;

    private InMemoryOperationalTaskRepository tasks;
    private CompetitorDetailBatchTakeover takeover;

    @BeforeEach
    void setUp() {
        tasks = new InMemoryOperationalTaskRepository();
        OperationalTaskService taskService = new OperationalTaskService(
                tasks,
                Clock.fixed(
                        Instant.parse("2026-07-28T01:00:00Z"),
                        ZoneOffset.UTC
                )
        );
        CompetitorRefreshExecutionFinalizer finalizer =
                CompetitorRefreshExecutionFinalizer.unfenced(mapper, taskService);
        takeover = new CompetitorDetailBatchTakeover(
                mapper, taskService, finalizer
        );
        tasks.insert(task(
                CURRENT_TASK_ID,
                OperationalTaskStatus.RUNNING,
                "day-2"
        ));
        org.mockito.Mockito.lenient().when(mapper.selectSearchRunById(CURRENT_RUN_ID))
                .thenReturn(run(CURRENT_TASK_ID, CURRENT_RUN_ID, "RUNNING"));
    }

    @Test
    void d1CallbackTerminatesOlderQueuedAndRunningChainsWithoutLatestOrFailure() {
        CompetitorDetailTakeoverCandidateRow queued =
                candidate(150100L, 220100L, "QUEUED", "day-0");
        CompetitorDetailTakeoverCandidateRow running =
                candidate(150200L, 220200L, "RUNNING", "day-1");
        when(mapper.listScheduledDetailOwnershipCandidates(
                WATCH_ID, CURRENT_TASK_ID, CURRENT_RUN_ID
        )).thenReturn(List.of(queued, running));
        stubSuccessfulSupersede(queued);
        stubSuccessfulSupersede(running);

        CompetitorDetailBatchTakeoverOutcome outcome =
                takeover.takeoverOlderBatches(
                        CURRENT_TASK_ID, CURRENT_RUN_ID, WATCH_ID
                );

        assertEquals(2, outcome.getOlderSuperseded());
        assertFalse(outcome.isCurrentSuperseded());
        InOrder locks = inOrder(mapper);
        locks.verify(mapper).lockActiveScheduledDetailTask(150100L);
        locks.verify(mapper).lockActiveScheduledDetailRun(
                150100L, 220100L, WATCH_ID
        );
        locks.verify(mapper).lockActiveScheduledDetailTask(150200L);
        locks.verify(mapper).lockActiveScheduledDetailRun(
                150200L, 220200L, WATCH_ID
        );
        verify(mapper, never()).updateLatestRefreshRunIfNotOlder(
                anyLong(), anyLong(), any(), any()
        );
        verify(mapper, never()).failRunningRefreshRun(
                anyLong(), anyLong(), anyLong(), any(), any(), any()
        );
        verify(mapper, never()).markActiveSearchRunFailedForTask(
                anyLong(), anyLong(), any(), any()
        );
    }

    @Test
    void sameBatchAndMalformedCandidatesCannotTakeOwnership() {
        CompetitorDetailTakeoverCandidateRow sameBatch =
                candidate(150200L, 220200L, "RUNNING", "day-2");
        CompetitorDetailTakeoverCandidateRow malformed =
                candidate(150400L, 220400L, "RUNNING", "day-3");
        malformed.setPayloadJson("{malformed");
        when(mapper.listScheduledDetailOwnershipCandidates(
                WATCH_ID, CURRENT_TASK_ID, CURRENT_RUN_ID
        )).thenReturn(List.of(sameBatch, malformed));

        CompetitorDetailBatchTakeoverOutcome outcome =
                takeover.takeoverOlderBatches(
                        CURRENT_TASK_ID, CURRENT_RUN_ID, WATCH_ID
                );

        assertEquals(0, outcome.getOlderSuperseded());
        assertFalse(outcome.isCurrentSuperseded());
        verify(mapper, never()).lockActiveScheduledDetailTask(anyLong());
    }

    @Test
    void d0StartSupersedesItselfWhenD1QueuedAlreadyOwnsWatch() {
        assertCurrentSuppressedBy("QUEUED");
    }

    @Test
    void d0StartSupersedesItselfWhenD1SucceededAlreadyOwnsWatch() {
        assertCurrentSuppressedBy("SUCCEEDED");
    }

    @Test
    void staleRecoveryGuardSupersedesD0InsteadOfCreatingReplacement() {
        OperationalTask stale = task(
                150100L, OperationalTaskStatus.RUNNING, "day-0"
        );
        CompetitorSearchRunRow staleRun = run(150100L, 220100L, "RUNNING");
        CompetitorDetailTakeoverCandidateRow newer =
                candidate(150300L, 220300L, "SUCCEEDED", "day-2");
        when(mapper.listScheduledDetailOwnershipCandidates(
                WATCH_ID, 150100L, 220100L
        )).thenReturn(List.of(newer));
        when(mapper.lockActiveScheduledDetailTask(150100L))
                .thenReturn("RUNNING");
        when(mapper.lockActiveScheduledDetailRun(
                150100L, 220100L, WATCH_ID
        )).thenReturn("RUNNING");
        when(mapper.supersedeActiveScheduledDetailTask(
                eq(150100L), eq("RUNNING"), any(), any()
        )).thenReturn(1);
        when(mapper.supersedeActiveScheduledDetailRun(
                150100L, 220100L, WATCH_ID, "RUNNING"
        )).thenReturn(1);

        assertTrue(takeover.supersedeStaleIfNewerBatchExists(
                stale, staleRun, WATCH_ID
        ));
        verify(mapper, never()).markActiveSearchRunFailedForTask(
                anyLong(), anyLong(), any(), any()
        );
    }

    @Test
    void strictRunCasFailureThrowsForTransactionalRollback() {
        CompetitorDetailTakeoverCandidateRow old =
                candidate(150100L, 220100L, "RUNNING", "day-0");
        when(mapper.listScheduledDetailOwnershipCandidates(
                WATCH_ID, CURRENT_TASK_ID, CURRENT_RUN_ID
        )).thenReturn(List.of(old));
        when(mapper.lockActiveScheduledDetailTask(150100L))
                .thenReturn("RUNNING");
        when(mapper.lockActiveScheduledDetailRun(
                150100L, 220100L, WATCH_ID
        )).thenReturn("RUNNING");
        when(mapper.supersedeActiveScheduledDetailTask(
                eq(150100L), eq("RUNNING"), any(), any()
        )).thenReturn(1);
        when(mapper.supersedeActiveScheduledDetailRun(
                150100L, 220100L, WATCH_ID, "RUNNING"
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> takeover.takeoverOlderBatches(
                        CURRENT_TASK_ID, CURRENT_RUN_ID, WATCH_ID
                )
        );
    }

    private void assertCurrentSuppressedBy(String newerStatus) {
        CompetitorDetailTakeoverCandidateRow newer =
                candidate(150400L, 220400L, newerStatus, "day-3");
        when(mapper.listScheduledDetailOwnershipCandidates(
                WATCH_ID, CURRENT_TASK_ID, CURRENT_RUN_ID
        )).thenReturn(List.of(newer));
        when(mapper.lockActiveScheduledDetailTask(CURRENT_TASK_ID))
                .thenReturn("RUNNING");
        when(mapper.lockActiveScheduledDetailRun(
                CURRENT_TASK_ID, CURRENT_RUN_ID, WATCH_ID
        )).thenReturn("RUNNING");
        when(mapper.supersedeActiveScheduledDetailTask(
                eq(CURRENT_TASK_ID), eq("RUNNING"), any(), any()
        )).thenReturn(1);
        when(mapper.supersedeActiveScheduledDetailRun(
                CURRENT_TASK_ID, CURRENT_RUN_ID, WATCH_ID, "RUNNING"
        )).thenReturn(1);

        CompetitorDetailBatchTakeoverOutcome outcome =
                takeover.takeoverOlderBatches(
                        CURRENT_TASK_ID, CURRENT_RUN_ID, WATCH_ID
                );

        assertTrue(outcome.isCurrentSuperseded());
        assertEquals(0, outcome.getOlderSuperseded());
        verify(mapper).supersedeActiveScheduledDetailTask(
                eq(CURRENT_TASK_ID),
                eq("RUNNING"),
                org.mockito.ArgumentMatchers.contains(
                        "SUPERSEDED_BY_NEW_DETAIL_BATCH"
                ),
                org.mockito.ArgumentMatchers.contains(
                        "supersedingRunId=220400"
                )
        );
    }

    private void stubSuccessfulSupersede(
            CompetitorDetailTakeoverCandidateRow candidate
    ) {
        when(mapper.lockActiveScheduledDetailTask(candidate.getTaskId()))
                .thenReturn(candidate.getTaskStatus());
        when(mapper.lockActiveScheduledDetailRun(
                candidate.getTaskId(), candidate.getRunId(), WATCH_ID
        )).thenReturn(candidate.getRunStatus());
        when(mapper.supersedeActiveScheduledDetailTask(
                eq(candidate.getTaskId()),
                eq(candidate.getTaskStatus()),
                any(),
                any()
        )).thenReturn(1);
        when(mapper.supersedeActiveScheduledDetailRun(
                candidate.getTaskId(),
                candidate.getRunId(),
                WATCH_ID,
                candidate.getRunStatus()
        )).thenReturn(1);
    }

    private static OperationalTask task(
            long taskId,
            OperationalTaskStatus status,
            String batchKey
    ) {
        OperationalTask task = new OperationalTask();
        task.setId(taskId);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:" + WATCH_ID + ":detail:" + batchKey);
        task.setStatus(status);
        task.setPayloadJson(payload(batchKey));
        return task;
    }

    private static CompetitorSearchRunRow run(
            long taskId,
            long runId,
            String status
    ) {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setTaskId(taskId);
        run.setId(runId);
        run.setWatchProductId(WATCH_ID);
        run.setTriggerMode(CompetitorRefreshExecutionMode.SCHEDULED_DETAIL.triggerMode());
        run.setStatus(status);
        return run;
    }

    private static CompetitorDetailTakeoverCandidateRow candidate(
            long taskId,
            long runId,
            String status,
            String batchKey
    ) {
        CompetitorDetailTakeoverCandidateRow candidate = new CompetitorDetailTakeoverCandidateRow();
        candidate.setTaskId(taskId);
        candidate.setRunId(runId);
        candidate.setTaskStatus(status);
        candidate.setRunStatus(status);
        candidate.setPayloadJson(payload(batchKey));
        return candidate;
    }

    private static String payload(String batchKey) {
        return "{\"watchProductId\":" + WATCH_ID
                + ",\"triggerMode\":\"SCHEDULED_DETAIL_MONITOR\",\"executionMode\":\"detail\",\"detailRefresh\":true"
                + ",\"batchKey\":\"" + batchKey + "\"}";
    }
}
