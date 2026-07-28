package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailRequest;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorDetailTakeoverMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorDetailBatchTerminalOwnershipTest {
    private static final long WATCH_ID = 180123L;
    private static final long CURRENT_TASK_ID = 150300L;
    private static final long CURRENT_RUN_ID = 220300L;

    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private NoonProductDetailAdapter detailAdapter;
    @Mock private CompetitorProductSnapshotService snapshotService;

    private CompetitorDetailBatchTakeover takeover;

    @BeforeEach
    void setUp() {
        InMemoryOperationalTaskRepository tasks =
                new InMemoryOperationalTaskRepository();
        tasks.insert(task());
        OperationalTaskService taskService = new OperationalTaskService(
                tasks,
                Clock.fixed(
                        Instant.parse("2026-07-28T01:00:00Z"),
                        ZoneOffset.UTC
                )
        );
        takeover = new CompetitorDetailBatchTakeover(
                mapper,
                taskService,
                CompetitorRefreshExecutionFinalizer.unfenced(
                        mapper, taskService
                )
        );
        lenient().when(mapper.selectSearchRunById(CURRENT_RUN_ID))
                .thenReturn(currentRun());
    }

    @ParameterizedTest
    @MethodSource("ownershipPairs")
    void exactOwnershipPairsSupersedeOlderGeneration(
            String taskStatus,
            String runStatus
    ) {
        stubNewerCandidate(taskStatus, runStatus);
        stubCurrentSupersede();

        CompetitorDetailBatchTakeoverOutcome outcome =
                takeover.takeoverOlderBatches(
                        CURRENT_TASK_ID, CURRENT_RUN_ID, WATCH_ID
                );

        assertTrue(outcome.isCurrentSuperseded());
    }

    @ParameterizedTest
    @MethodSource("nonOwnershipPairs")
    void inconsistentOrUntrustedPairsDoNotOwnGeneration(
            String taskStatus,
            String runStatus
    ) {
        stubNewerCandidate(taskStatus, runStatus);

        CompetitorDetailBatchTakeoverOutcome outcome =
                takeover.takeoverOlderBatches(
                        CURRENT_TASK_ID, CURRENT_RUN_ID, WATCH_ID
                );

        assertFalse(outcome.isCurrentSuperseded());
        verify(mapper, never()).lockActiveScheduledDetailTask(CURRENT_TASK_ID);
    }

    @ParameterizedTest
    @MethodSource("terminalOwnershipPairs")
    void terminalNewDayStopsDelayedOldDayBeforeHttp(
            String taskStatus,
            String runStatus
    ) {
        stubNewerCandidate(taskStatus, runStatus);
        stubCurrentSupersede();
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(WATCH_ID))
                .thenReturn(List.of());
        CompetitorProductDetailRefreshService detailService =
                new CompetitorProductDetailRefreshService(
                        mapper,
                        detailAdapter,
                        snapshotService,
                        Clock.systemUTC()
                );

        assertThrows(
                CompetitorRefreshLeaseLostException.class,
                () -> detailService.refreshConfirmedCompetitors(
                        watchProduct(),
                        CURRENT_RUN_ID,
                        CURRENT_TASK_ID,
                        501L,
                        new CompetitorDetailBatchTakeoverFence(
                                takeover,
                                CURRENT_TASK_ID,
                                CURRENT_RUN_ID,
                                WATCH_ID
                        )
                )
        );

        verify(detailAdapter, never()).fetch(any(NoonProductDetailRequest.class));
        verify(mapper, never()).updateCompetitorProductFromDetail(any());
    }

    @Test
    void candidateSqlIncludesOnlyTrustedActiveAndTerminalPairs()
            throws Exception {
        Method method = CompetitorDetailTakeoverMapper.class.getMethod(
                "listScheduledDetailOwnershipCandidates",
                Long.class,
                Long.class,
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains(
                "task.status IN ('QUEUED', 'RUNNING') "
                        + "AND run.status IN ('QUEUED', 'RUNNING')"
        ));
        assertTrue(sql.contains(
                "task.status = 'SUCCEEDED' "
                        + "AND run.status IN ('SUCCEEDED', 'PARTIAL_FAILED')"
        ));
        assertTrue(sql.contains(
                "task.status = 'FAILED' "
                        + "AND run.status IN ('FAILED', 'PARTIAL_FAILED')"
        ));
    }

    private void stubNewerCandidate(
            String taskStatus,
            String runStatus
    ) {
        when(mapper.listScheduledDetailOwnershipCandidates(
                WATCH_ID, CURRENT_TASK_ID, CURRENT_RUN_ID
        )).thenReturn(List.of(candidate(taskStatus, runStatus)));
    }

    private void stubCurrentSupersede() {
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
    }

    private static Stream<Arguments> ownershipPairs() {
        return Stream.of(
                Arguments.of("QUEUED", "QUEUED"),
                Arguments.of("RUNNING", "RUNNING"),
                Arguments.of("SUCCEEDED", "SUCCEEDED"),
                Arguments.of("SUCCEEDED", "PARTIAL_FAILED"),
                Arguments.of("FAILED", "PARTIAL_FAILED"),
                Arguments.of("FAILED", "FAILED")
        );
    }

    private static Stream<Arguments> terminalOwnershipPairs() {
        return Stream.of(
                Arguments.of("SUCCEEDED", "PARTIAL_FAILED"),
                Arguments.of("FAILED", "PARTIAL_FAILED"),
                Arguments.of("FAILED", "FAILED")
        );
    }

    private static Stream<Arguments> nonOwnershipPairs() {
        return Stream.of(
                Arguments.of("FAILED", "QUEUED"),
                Arguments.of("FAILED", "RUNNING"),
                Arguments.of("SUCCEEDED", "FAILED"),
                Arguments.of("QUEUED", "RUNNING"),
                Arguments.of("RUNNING", "QUEUED"),
                Arguments.of("CANCELLED", "CANCELLED")
        );
    }

    private static OperationalTask task() {
        OperationalTask task = new OperationalTask();
        task.setId(CURRENT_TASK_ID);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:" + WATCH_ID + ":detail:day-0");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setPayloadJson(payload("day-0"));
        return task;
    }

    private static CompetitorSearchRunRow currentRun() {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(CURRENT_RUN_ID);
        run.setTaskId(CURRENT_TASK_ID);
        run.setWatchProductId(WATCH_ID);
        run.setTriggerMode(
                CompetitorRefreshExecutionMode.SCHEDULED_DETAIL.triggerMode()
        );
        run.setStatus("RUNNING");
        return run;
    }

    private static CompetitorDetailTakeoverCandidateRow candidate(
            String taskStatus,
            String runStatus
    ) {
        CompetitorDetailTakeoverCandidateRow candidate =
                new CompetitorDetailTakeoverCandidateRow();
        candidate.setTaskId(150400L);
        candidate.setRunId(220400L);
        candidate.setTaskStatus(taskStatus);
        candidate.setRunStatus(runStatus);
        candidate.setPayloadJson(payload("day-1"));
        return candidate;
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow watch = new CompetitorWatchProductRow();
        watch.setId(WATCH_ID);
        watch.setSiteCode("SA");
        watch.setSelfNoonProductCode("ZSELF001");
        return watch;
    }

    private static String payload(String batchKey) {
        return "{\"watchProductId\":" + WATCH_ID
                + ",\"triggerMode\":\"SCHEDULED_DETAIL_MONITOR\""
                + ",\"executionMode\":\"detail\",\"detailRefresh\":true"
                + ",\"batchKey\":\"" + batchKey + "\"}";
    }
}
