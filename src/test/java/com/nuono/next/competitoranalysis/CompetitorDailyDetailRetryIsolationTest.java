package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorDailyDetailRetryIsolationTest {
    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private CompetitorMonitoringMapper monitoringMapper;
    @Mock private CompetitorKeywordRefreshTransactionRunner keywordRunner;
    @Mock private CompetitorProductDetailRefreshService detailService;

    private InMemoryOperationalTaskRepository taskRepository;
    private List<Runnable> submitted;
    private Map<Long, CompetitorSearchRunRow> runsByTask;
    private CompetitorAnalysisRefreshService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-06T08:00:00Z"),
                ZoneOffset.UTC
        );
        taskRepository = new InMemoryOperationalTaskRepository();
        submitted = new ArrayList<>();
        runsByTask = new LinkedHashMap<>();
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(taskRepository, clock),
                (accountKey, task) -> submitted.add(task),
                keywordRunner,
                detailService,
                clock,
                NoonRiskBackoffGuard.disabled()
        );
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CompetitorSearchRunInsertCommand command = invocation.getArgument(0);
            runsByTask.put(command.getTaskId(), run(command));
            return 1;
        }).when(mapper).insertSearchRun(any());
        org.mockito.Mockito.lenient().when(mapper.selectSearchRunByTaskId(anyLong()))
                .thenAnswer(invocation -> runsByTask.get(invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(mapper.markSearchRunRunning(anyLong()))
                .thenReturn(1);
    }

    @Test
    void newDailyBatchRunsFullDetailWhilePreviousBatchRetryStillWaits() {
        CompetitorWatchProductRow product = product();
        OperationalTask previousRetry = previousDayRetryTask();
        taskRepository.insert(previousRetry);
        runsByTask.put(previousRetry.getId(), queuedRun(
                220122L,
                previousRetry.getId(),
                product.getId()
        ));
        CompetitorProductDetailTarget refreshed =
                CompetitorProductDetailTarget.self("ZSELF001");
        CompetitorProductDetailRefreshResult fullRefresh =
                CompetitorProductDetailRefreshResult.empty();
        fullRefresh.recordAttempt(refreshed);
        fullRefresh.recordSuccess(refreshed);
        when(monitoringMapper.selectRefreshableWatchProductBoundary(501L, "STORE", "SA"))
                .thenReturn(boundary());
        when(monitoringMapper.listRefreshableWatchProducts(
                anyLong(), any(), any(), anyLong(), anyLong(), anyInt()
        )).thenAnswer(invocation -> page(
                List.of(product),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5)
        ));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(product);
        when(detailService.currentTargets(product)).thenReturn(List.of(refreshed));
        when(detailService.refreshTargets(
                eq(product),
                eq(List.of(refreshed)),
                eq(220123L),
                anyLong(),
                org.mockito.ArgumentMatchers.isNull(),
                any(CompetitorDetailRetrySession.class)
        )).thenAnswer(CompetitorDetailRetryMockSupport.checkpointing(
                taskRepository, fullRefresh
        ));

        service.requestScheduledDetailMonitoring(501L, "STORE", "SA");
        submitted.get(0).run();

        List<OperationalTask> productTasks = taskRepository.tasks.values().stream()
                .filter(task -> CompetitorAnalysisRefreshService.TASK_TYPE.equals(task.getTaskType()))
                .sorted(java.util.Comparator.comparing(OperationalTask::getId))
                .collect(Collectors.toList());
        assertEquals(2, productTasks.size());
        OperationalTask currentDayTask = productTasks.get(1);
        assertTrue(currentDayTask.getPayloadJson().contains("\"detailRefresh\":true"));
        assertTrue(!currentDayTask.getPayloadJson().contains("\"batchKey\":\"previous-day\""));

        for (Runnable runnable : new ArrayList<>(submitted.subList(1, submitted.size()))) {
            runnable.run();
        }
        currentDayTask = taskRepository.selectById(currentDayTask.getId());
        assertEquals(OperationalTaskStatus.SUCCEEDED, currentDayTask.getStatus());
        assertEquals("竞品详情快照刷新完成。", currentDayTask.getMessage());
        assertTrue(currentDayTask.getNaturalKey().contains(":detail:"));
        verify(detailService).refreshTargets(
                eq(product),
                eq(List.of(refreshed)),
                eq(220123L),
                eq(currentDayTask.getId()),
                org.mockito.ArgumentMatchers.isNull(),
                any(CompetitorDetailRetrySession.class)
        );
        verify(detailService, never()).refreshTargets(
                eq(product),
                any(),
                eq(220122L),
                eq(previousRetry.getId()),
                org.mockito.ArgumentMatchers.isNull(),
                any(CompetitorDetailRetrySession.class)
        );
        verify(keywordRunner, never()).runKeyword(
                anyLong(), anyLong(), any(), any(), any()
        );
        verify(mapper, never()).listActiveKeywordsByWatchProductId(180123L);
    }

    private static List<CompetitorWatchProductRow> page(
            List<CompetitorWatchProductRow> products,
            long afterId,
            long upperId,
            int limit
    ) {
        return products.stream()
                .filter(product -> product.getId() > afterId && product.getId() <= upperId)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private static CompetitorMonitoringBoundaryRow boundary() {
        CompetitorMonitoringBoundaryRow row = new CompetitorMonitoringBoundaryRow();
        row.setEligibleTotal(1L);
        row.setUpperWatchProductId(180123L);
        return row;
    }

    private static CompetitorWatchProductRow product() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STORE");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode("ZSELF001");
        return row;
    }

    private static CompetitorSearchRunRow run(CompetitorSearchRunInsertCommand command) {
        return queuedRun(command.getId(), command.getTaskId(), command.getWatchProductId());
    }

    private static CompetitorSearchRunRow queuedRun(long runId, long taskId, long watchId) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(runId);
        row.setTaskId(taskId);
        row.setWatchProductId(watchId);
        row.setTriggerMode(CompetitorRefreshExecutionMode.SCHEDULED_DETAIL.triggerMode());
        row.setStatus("QUEUED");
        return row;
    }

    private static OperationalTask previousDayRetryTask() {
        OperationalTask task = new OperationalTask();
        task.setId(150000L);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:180123:detail");
        task.setOwnerUserId(501L);
        task.setStoreCode("STORE");
        task.setSiteCode("SA");
        task.setStatus(OperationalTaskStatus.QUEUED);
        task.setProgressPercent(5);
        task.setPayloadJson(
                "{\"watchProductId\":180123,"
                        + "\"executionMode\":\"detail\","
                        + "\"detailRefresh\":true,"
                        + "\"batchKey\":\"previous-day\","
                        + "\"retryAttempt\":3,"
                        + "\"maxRetryAttempts\":4,"
                        + "\"retryNotBefore\":\"2026-06-06T10:00:00\","
                        + "\"failedDetailTargets\":[{"
                        + "\"subjectType\":\"COMPETITOR\","
                        + "\"competitorProductId\":88002,"
                        + "\"noonProductCode\":\"ZFAIL002\"}]}"
        );
        task.setCreatedAt(LocalDateTime.parse("2026-06-05T08:00:00"));
        task.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:59:00"));
        return task;
    }
}
