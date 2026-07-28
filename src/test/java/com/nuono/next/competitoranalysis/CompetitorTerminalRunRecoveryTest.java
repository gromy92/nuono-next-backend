package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorTerminalRunRecoveryTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-06T08:00:00Z"),
            ZoneOffset.UTC
    );
    private static final LocalDateTime STALE_BEFORE =
            LocalDateTime.parse("2026-06-06T07:30:00");
    private static final String STALE_MESSAGE = "刷新任务超过 30 分钟未完成，已自动释放。";

    @Mock
    private CompetitorAnalysisMapper mapper;
    @Mock
    private CompetitorMonitoringMapper monitoringMapper;
    @Mock
    private OperationalTaskService operationalTaskService;
    @Mock
    private CompetitorKeywordRefreshTransactionRunner keywordRefreshRunner;
    @Mock
    private CompetitorTaskSubmitter taskSubmitter;

    @Test
    void backgroundRecoveryAlignsTerminalRunWithoutCreatingReplacement() {
        OperationalTask staleTask = staleTask();
        CompetitorSearchRunRow terminalRun = run(220001L, 150001L, "PARTIAL_FAILED");
        OperationalTask alignedTask = staleTask.copy();
        alignedTask.setStatus(OperationalTaskStatus.SUCCEEDED);
        when(operationalTaskService.failStaleRunning(
                150001L, STALE_BEFORE, "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(true);
        when(mapper.markActiveSearchRunFailedForTask(
                220001L, 150001L, "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(0);
        when(mapper.selectSearchRunByTaskId(150001L))
                .thenReturn(terminalRun, terminalRun);
        when(mapper.alignFailedStaleTaskToTerminalRun(
                150001L,
                "FAILED_STALE",
                "SUCCEEDED",
                null,
                "竞品刷新部分完成。"
        )).thenReturn(1);
        when(operationalTaskService.find(150001L)).thenReturn(Optional.of(alignedTask));
        CompetitorRefreshTaskFactory factory =
                new CompetitorRefreshTaskFactory(mapper, operationalTaskService);
        CompetitorRefreshRecoveryCoordinator coordinator = coordinator(factory);

        assertTrue(coordinator.recoverInterrupted(
                staleTask, watchProduct(), terminalRun, STALE_BEFORE
        ));

        verify(operationalTaskService, never()).queue(
                any(), any(), any(OperationalTaskPayload.class)
        );
        verify(taskSubmitter, never()).submit(any(), any());
    }

    @Test
    void manualRefreshReconcilesTerminalRunThenCreatesExplicitNewRefresh() {
        OperationalTask staleTask = staleTask();
        CompetitorSearchRunRow terminalRun = run(220001L, 150001L, "SUCCEEDED");
        OperationalTask alignedTask = staleTask.copy();
        alignedTask.setStatus(OperationalTaskStatus.SUCCEEDED);
        OperationalTask queuedTask = queuedTask();
        CompetitorSearchRunRow queuedRun = run(220002L, 150002L, "QUEUED");
        when(mapper.selectWatchProductById(501L, 180001L)).thenReturn(watchProduct());
        when(mapper.listActiveKeywordsByWatchProductId(180001L)).thenReturn(List.of(keyword()));
        when(operationalTaskService.findActive(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                "watchProduct:180001"
        )).thenReturn(Optional.of(staleTask), Optional.empty());
        when(mapper.selectSearchRunByTaskId(150001L))
                .thenReturn(terminalRun, terminalRun, terminalRun);
        when(operationalTaskService.failStaleRunning(
                150001L, STALE_BEFORE, "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(true);
        when(mapper.markActiveSearchRunFailedForTask(
                220001L, 150001L, "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(0);
        when(mapper.alignFailedStaleTaskToTerminalRun(
                150001L,
                "FAILED_STALE",
                "SUCCEEDED",
                null,
                "竞品刷新已完成。"
        )).thenReturn(1);
        when(operationalTaskService.find(150001L)).thenReturn(Optional.of(alignedTask));
        when(operationalTaskService.queue(any(), any(), any())).thenReturn(queuedTask);
        when(mapper.selectSearchRunByTaskId(150002L)).thenReturn(null, queuedRun);
        when(mapper.nextSearchRunId()).thenReturn(220002L);
        when(operationalTaskService.find(150002L)).thenReturn(Optional.of(queuedTask));
        CompetitorRefreshTaskFactory factory =
                new CompetitorRefreshTaskFactory(mapper, operationalTaskService);
        CompetitorAnalysisRefreshService service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                operationalTaskService,
                taskSubmitter,
                keywordRefreshRunner,
                null,
                CLOCK,
                NoonRiskBackoffGuard.disabled(),
                factory
        );

        CompetitorRefreshRunView result = service.requestRefresh(context(), 180001L);

        assertEquals(150002L, result.getTaskId());
        assertEquals(220002L, result.getRunId());
        assertEquals("QUEUED", result.getTaskStatus());
        verify(operationalTaskService).queue(
                eq(CompetitorAnalysisRefreshService.TASK_TYPE),
                eq("watchProduct:180001"),
                any(OperationalTaskPayload.class)
        );
        verify(taskSubmitter).submit(eq("501::STR108065-NSA"), any(Runnable.class));
    }

    @Test
    void failedTerminalRunKeepsTaskFailedAndOriginalError() {
        OperationalTask staleTask = staleTask();
        CompetitorSearchRunRow failedRun = run(220001L, 150001L, "FAILED");
        failedRun.setErrorCode("PUBLIC_DETAIL_NOT_FOUND");
        failedRun.setErrorMessage("Noon detail not found.");
        when(operationalTaskService.failStaleRunning(
                150001L, STALE_BEFORE, "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(true);
        when(mapper.markActiveSearchRunFailedForTask(
                220001L, 150001L, "FAILED_STALE", STALE_MESSAGE
        )).thenReturn(0);
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(failedRun);
        when(mapper.alignFailedStaleTaskToTerminalRun(
                150001L,
                "FAILED_STALE",
                "FAILED",
                "PUBLIC_DETAIL_NOT_FOUND",
                "Noon detail not found."
        )).thenReturn(1);

        CompetitorStaleTaskReconciler.Outcome outcome =
                new CompetitorStaleTaskReconciler(mapper, operationalTaskService)
                        .claim(staleTask, failedRun, STALE_BEFORE, "FAILED_STALE", STALE_MESSAGE);

        assertEquals(CompetitorStaleTaskReconciler.Outcome.TERMINAL_RECONCILED, outcome);
    }

    @Test
    void terminalTaskAlignmentIsBoundToTheClaimedFailedCompetitorTask() throws Exception {
        Method method = CompetitorAnalysisMapper.class.getMethod(
                "alignFailedStaleTaskToTerminalRun",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("id = #{taskId}"));
        assertTrue(sql.contains("task_type = 'OPERATIONS_COMPETITOR_REFRESH'"));
        assertTrue(sql.contains("status = 'FAILED'"));
        assertTrue(sql.contains("error_code = #{claimedErrorCode}"));
    }

    private CompetitorRefreshRecoveryCoordinator coordinator(
            CompetitorRefreshTaskFactory factory
    ) {
        return new CompetitorRefreshRecoveryCoordinator(
                mapper,
                operationalTaskService,
                factory,
                new CompetitorRefreshTaskDispatcher(
                        mapper, operationalTaskService, taskSubmitter
                ),
                ignored -> true,
                (taskId, runId, watchProductId, actorUserId, mode) -> {
                },
                CLOCK
        );
    }

    private static OperationalTask staleTask() {
        OperationalTask task = new OperationalTask();
        task.setId(150001L);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:180001");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        return task;
    }

    private static OperationalTask queuedTask() {
        OperationalTask task = staleTask();
        task.setId(150002L);
        task.setStatus(OperationalTaskStatus.QUEUED);
        task.setPayloadJson("{\"watchProductId\":180001}");
        return task;
    }

    private static CompetitorSearchRunRow run(long id, long taskId, String status) {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(id);
        run.setTaskId(taskId);
        run.setWatchProductId(180001L);
        run.setStatus(status);
        run.setTriggerMode("MANUAL_REFRESH");
        run.setRequestedBy(90001L);
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

    private static CompetitorKeywordRow keyword() {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(190001L);
        row.setWatchProductId(180001L);
        row.setKeyword("laundry basket");
        return row;
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(java.util.Set.of("STR108065-NSA"))
                .storeOwnerUserIds(java.util.Map.of("STR108065-NSA", 501L))
                .menuPaths(java.util.Set.of("/operations/competitor-analysis"))
                .build();
    }
}
