package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorScheduledDetailRootOwnershipTest {
    private static final long WATCH_ID = 180123L;
    private static final LocalDateTime STALE_BEFORE =
            LocalDateTime.parse("2026-07-28T08:30:00");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T01:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private OperationalTaskService operationalTaskService;

    @Test
    void staleFreshReplacementFreezesOriginalRunAsReadyChainRoot() throws Exception {
        OperationalTask staleTask = task(
                150100L,
                OperationalTaskStatus.RUNNING,
                payload("day-0", null)
        );
        CompetitorSearchRunRow staleRun = run(150100L, 220100L, "RUNNING");
        AtomicReference<String> queuedPayload = new AtomicReference<>();
        AtomicReference<OperationalTask> queuedTask = new AtomicReference<>();
        when(mapper.listScheduledDetailOwnershipCandidates(
                WATCH_ID, 150100L, 220100L
        )).thenReturn(List.of());
        when(operationalTaskService.failStaleRunning(
                eq(150100L), eq(STALE_BEFORE), eq("FAILED_STALE"), any()
        )).thenReturn(true);
        when(mapper.markActiveSearchRunFailedForTask(
                eq(220100L), eq(150100L), eq("FAILED_STALE"), any()
        )).thenReturn(1);
        when(operationalTaskService.queue(any(), any(), any()))
                .thenAnswer(invocation -> {
                    OperationalTaskPayload submitted = invocation.getArgument(2);
                    queuedPayload.set(submitted.getPayloadJson());
                    OperationalTask queued = task(
                            150400L,
                            OperationalTaskStatus.QUEUED,
                            submitted.getPayloadJson()
                    );
                    queuedTask.set(queued);
                    return queued;
                });
        when(mapper.nextSearchRunId()).thenReturn(220400L);
        when(mapper.selectSearchRunByTaskId(150400L)).thenReturn(
                null,
                run(150400L, 220400L, "QUEUED")
        );
        when(operationalTaskService.find(150400L))
                .thenAnswer(ignored -> Optional.ofNullable(queuedTask.get()));

        CompetitorRefreshTaskFactory factory =
                new CompetitorRefreshTaskFactory(mapper, operationalTaskService);
        CompetitorRefreshRecoveryCoordinator coordinator =
                new CompetitorRefreshRecoveryCoordinator(
                        mapper,
                        operationalTaskService,
                        factory,
                        new CompetitorRefreshTaskDispatcher(
                                mapper,
                                operationalTaskService,
                                (accountKey, task) -> { }
                        ),
                        ignored -> true,
                        (taskId, runId, watchProductId, actorUserId, mode) -> { },
                        CLOCK
                );

        assertTrue(coordinator.recoverInterrupted(
                staleTask, watchProduct(), staleRun, STALE_BEFORE
        ));
        assertEquals(150400L, queuedTask.get().getId());
        JsonNode persisted = new ObjectMapper().readTree(queuedPayload.get());
        assertEquals(220100L, persisted.path("rootRunId").asLong());
        CompetitorDetailRetryPayload retry =
                CompetitorDetailRetryPayload.fromJson(queuedPayload.get());
        assertFalse(retry.isInitialized());
        assertTrue(retry.getRetryStates().isEmpty());
        assertEquals(
                220100L,
                new ObjectMapper().readTree(retry.toJson()).path("rootRunId").asLong()
        );
        assertTrue(CompetitorRefreshRecoveryPayload.isReady(
                queuedTask.get(), LocalDateTime.parse("2026-07-28T09:00:00")
        ));
    }

    @Test
    void retrySessionInitializationPreservesFrozenRoot() throws Exception {
        OperationalTask replacement = task(
                150400L,
                OperationalTaskStatus.RUNNING,
                payload("day-0", 220100L)
        );
        CompetitorRefreshExecutionFinalizer finalizer =
                mock(CompetitorRefreshExecutionFinalizer.class);
        CompetitorRefreshTaskFactory factory = new CompetitorRefreshTaskFactory(
                mapper, operationalTaskService, finalizer
        );

        new CompetitorDetailRetrySession(
                factory,
                replacement,
                220400L,
                WATCH_ID,
                List.of(CompetitorProductDetailTarget.self("ZSELF001")),
                CLOCK,
                null
        );

        CompetitorDetailRetryPayload retry =
                CompetitorDetailRetryPayload.fromJson(replacement.getPayloadJson());
        assertTrue(retry.isInitialized());
        assertEquals(220100L, retry.getRootRunId());
        assertEquals(220400L, retry.getRetryOfRunId());
        assertEquals(1, retry.getRetryStates().size());
        verify(finalizer).checkpointDetailRetry(
                eq(150400L), eq(220400L), eq(WATCH_ID), any()
        );
    }

    @Test
    void higherReplacementRunWithFrozenOlderRootCannotReverseTakeOverD1() {
        InMemoryOperationalTaskRepository tasks =
                new InMemoryOperationalTaskRepository();
        OperationalTaskService taskService = new OperationalTaskService(
                tasks, CLOCK
        );
        CompetitorRefreshExecutionFinalizer finalizer =
                CompetitorRefreshExecutionFinalizer.unfenced(mapper, taskService);
        CompetitorDetailBatchTakeover takeover =
                new CompetitorDetailBatchTakeover(mapper, taskService, finalizer);
        tasks.insert(task(
                150300L,
                OperationalTaskStatus.RUNNING,
                payload("day-1", null)
        ));
        CompetitorDetailTakeoverCandidateRow replacement = candidate(
                150400L,
                220400L,
                "QUEUED",
                payload("day-0", 220100L)
        );
        when(mapper.selectSearchRunById(220300L))
                .thenReturn(run(150300L, 220300L, "RUNNING"));
        when(mapper.listScheduledDetailOwnershipCandidates(
                WATCH_ID, 150300L, 220300L
        )).thenReturn(List.of(replacement));
        when(mapper.lockActiveScheduledDetailTask(150400L))
                .thenReturn("QUEUED");
        when(mapper.lockActiveScheduledDetailRun(
                150400L, 220400L, WATCH_ID
        )).thenReturn("QUEUED");
        when(mapper.supersedeActiveScheduledDetailTask(
                eq(150400L), eq("QUEUED"), any(), any()
        )).thenReturn(1);
        when(mapper.supersedeActiveScheduledDetailRun(
                150400L, 220400L, WATCH_ID, "QUEUED"
        )).thenReturn(1);

        CompetitorDetailBatchTakeoverOutcome outcome =
                takeover.takeoverOlderBatches(150300L, 220300L, WATCH_ID);

        assertFalse(outcome.isCurrentSuperseded());
        assertEquals(1, outcome.getOlderSuperseded());
        verify(mapper, never()).lockActiveScheduledDetailTask(150300L);
    }

    @Test
    void malformedExistingRootFailsBeforeClaimingStaleGeneration() {
        OperationalTask staleTask = task(
                150100L,
                OperationalTaskStatus.RUNNING,
                payload("day-0", "\"corrupt\"")
        );

        assertThrows(
                CompetitorRefreshRecoveryPayloadException.class,
                () -> new CompetitorRefreshTaskFactory(
                        mapper, operationalTaskService
                ).replaceStale(
                        staleTask,
                        run(150100L, 220100L, "RUNNING"),
                        watchProduct(),
                        STALE_BEFORE,
                        501L,
                        CompetitorRefreshExecutionMode.SCHEDULED_DETAIL,
                        "day-0",
                        0,
                        ignored -> { }
                )
        );

        verify(operationalTaskService, never()).failStaleRunning(
                any(), any(), any(), any()
        );
        verify(operationalTaskService, never()).queue(any(), any(), any());
    }

    private static OperationalTask task(
            long taskId,
            OperationalTaskStatus status,
            String payloadJson
    ) {
        OperationalTask task = new OperationalTask();
        task.setId(taskId);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:" + WATCH_ID + ":detail");
        task.setStatus(status);
        task.setPayloadJson(payloadJson);
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
        run.setTriggerMode(
                CompetitorRefreshExecutionMode.SCHEDULED_DETAIL.triggerMode()
        );
        run.setStatus(status);
        run.setRequestedBy(501L);
        return run;
    }

    private static CompetitorDetailTakeoverCandidateRow candidate(
            long taskId,
            long runId,
            String status,
            String payloadJson
    ) {
        CompetitorDetailTakeoverCandidateRow candidate =
                new CompetitorDetailTakeoverCandidateRow();
        candidate.setTaskId(taskId);
        candidate.setRunId(runId);
        candidate.setTaskStatus(status);
        candidate.setRunStatus(status);
        candidate.setPayloadJson(payloadJson);
        return candidate;
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(WATCH_ID);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        return row;
    }

    private static String payload(String batchKey, Object rootRunId) {
        return "{\"watchProductId\":" + WATCH_ID
                + ",\"triggerMode\":\"SCHEDULED_DETAIL_MONITOR\","
                + "\"executionMode\":\"detail\",\"rankRefresh\":false,"
                + "\"detailRefresh\":true,"
                + "\"batchKey\":\"" + batchKey + "\""
                + (rootRunId == null ? "" : ",\"rootRunId\":" + rootRunId)
                + "}";
    }
}
