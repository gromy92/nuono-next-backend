package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffRepository;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CompetitorAnalysisDetailRiskBackoffTest {

    @Test
    void scheduledDetailMonitoringRecordsLaterRiskFailureInsteadOfFirstOrdinaryFailure() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        CompetitorMonitoringMapper monitoringMapper = mock(CompetitorMonitoringMapper.class);
        OperationalTaskService taskService = mock(OperationalTaskService.class);
        CompetitorKeywordRefreshTransactionRunner keywordRunner =
                mock(CompetitorKeywordRefreshTransactionRunner.class);
        CompetitorProductDetailRefreshService detailService =
                mock(CompetitorProductDetailRefreshService.class);
        NoonRiskBackoffRepository riskRepository = mock(NoonRiskBackoffRepository.class);
        List<Runnable> submittedTasks = new ArrayList<>();
        Clock clock = Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC);
        CompetitorAnalysisRefreshService service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                taskService,
                (accountKey, task) -> submittedTasks.add(task),
                keywordRunner,
                detailService,
                clock,
                new NoonRiskBackoffGuard(riskRepository, clock)
        );
        CompetitorWatchProductRow watchProduct = watchProduct();
        OperationalTask monitorTask = task(
                150000L,
                CompetitorAnalysisRefreshService.MONITOR_TASK_TYPE,
                "store:501:STR108065-NSA:SA:scheduledDetail",
                OperationalTaskStatus.QUEUED
        );
        OperationalTask productTask = task(
                150001L,
                CompetitorAnalysisRefreshService.TASK_TYPE,
                "watchProduct:180123:scheduledDetail",
                OperationalTaskStatus.QUEUED
        );
        CompetitorProductDetailRefreshResult detailResult = detailFailureWithLaterRisk();
        CompetitorSearchRunRow searchRun = searchRun();

        when(monitoringMapper.selectRefreshableWatchProductBoundary(501L, "STR108065-NSA", "SA"))
                .thenReturn(boundary());
        when(monitoringMapper.listRefreshableWatchProducts(
                eq(501L), eq("STR108065-NSA"), eq("SA"), any(), eq(180123L), eq(500)
        )).thenReturn(List.of(watchProduct), List.of());
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);
        when(taskService.findActive(anyString(), anyString())).thenReturn(Optional.empty());
        when(taskService.queue(
                eq(CompetitorAnalysisRefreshService.MONITOR_TASK_TYPE),
                anyString(),
                any(OperationalTaskPayload.class)
        )).thenAnswer(invocation -> {
            monitorTask.setPayloadJson(((OperationalTaskPayload) invocation.getArgument(2)).getPayloadJson());
            return monitorTask;
        });
        when(taskService.queue(
                eq(CompetitorAnalysisRefreshService.TASK_TYPE),
                anyString(),
                any(OperationalTaskPayload.class)
        )).thenAnswer(invocation -> {
            productTask.setPayloadJson(((OperationalTaskPayload) invocation.getArgument(2)).getPayloadJson());
            return productTask;
        });
        when(taskService.claimQueued(150000L, CompetitorMonitoringBatchRunner.RUNNING_MESSAGE)).thenReturn(true);
        when(taskService.claimQueued(150001L, "竞品刷新正在后台执行。")).thenReturn(true);
        when(taskService.checkpointRunning(
                eq(150000L), anyString(), any(), eq(CompetitorMonitoringBatchRunner.RUNNING_MESSAGE)
        )).thenReturn(true);
        when(taskService.listActiveAfter(CompetitorAnalysisRefreshService.TASK_TYPE, 0L, 1000))
                .thenReturn(List.of(productTask));
        when(taskService.listActiveAfter(CompetitorAnalysisRefreshService.TASK_TYPE, 150001L, 1000))
                .thenReturn(List.of());
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(searchRun);
        when(mapper.markSearchRunRunning(220123L)).thenReturn(1);
        when(detailService.refreshConfirmedCompetitors(
                eq(watchProduct),
                eq(220123L),
                eq(150001L),
                isNull()
        )).thenReturn(detailResult);

        service.requestScheduledDetailMonitoring(501L, "STR108065-NSA", "SA");
        submittedTasks.get(0).run();
        submittedTasks.get(1).run();

        verify(taskService).fail(
                eq(150001L),
                eq("COMPETITOR_RISK_BACKOFF"),
                contains("rate_limited")
        );
        verify(mapper).completeSearchRun(
                eq(220123L),
                eq("FAILED"),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                eq("RATE_LIMITED"),
                contains("429"),
                isNull()
        );
        ArgumentCaptor<NoonRiskBackoffHold> holds = ArgumentCaptor.forClass(NoonRiskBackoffHold.class);
        verify(riskRepository, times(2)).upsert(holds.capture());
        assertTrue(holds.getAllValues().stream().allMatch(hold ->
                "rate_limited".equals(hold.getRiskType())
                        && Long.valueOf(150001L).equals(hold.getSourceTaskId())
        ));
    }

    private static CompetitorProductDetailRefreshResult detailFailureWithLaterRisk() {
        CompetitorProductDetailRefreshResult result = CompetitorProductDetailRefreshResult.empty();
        result.recordAttempt();
        result.recordFailure("DETAIL_REFRESH_FAILED", "Noon detail parse failed");
        result.recordAttempt();
        result.recordFailure("RATE_LIMITED", "Noon 前台商品详情返回 HTTP 429。");
        return result;
    }

    private static CompetitorMonitoringBoundaryRow boundary() {
        CompetitorMonitoringBoundaryRow row = new CompetitorMonitoringBoundaryRow();
        row.setEligibleTotal(1L);
        row.setUpperWatchProductId(180123L);
        return row;
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("BASKET-SA-001-BLUE");
        row.setSelfNoonProductCode("ZSELF001");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorSearchRunRow searchRun() {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(220123L);
        row.setTaskId(150001L);
        row.setWatchProductId(180123L);
        row.setTriggerMode(CompetitorRefreshExecutionMode.SCHEDULED_DETAIL.triggerMode());
        row.setStatus("QUEUED");
        return row;
    }

    private static OperationalTask task(
            Long id,
            String taskType,
            String naturalKey,
            OperationalTaskStatus status
    ) {
        OperationalTask task = new OperationalTask();
        task.setId(id);
        task.setTaskType(taskType);
        task.setNaturalKey(naturalKey);
        task.setOwnerUserId(501L);
        task.setStoreCode("STR108065-NSA");
        task.setSiteCode("SA");
        task.setStatus(status);
        task.setProgressPercent(0);
        task.setCreatedAt(LocalDateTime.parse("2026-06-06T08:00:00"));
        task.setUpdatedAt(LocalDateTime.parse("2026-06-06T08:00:00"));
        return task;
    }
}
