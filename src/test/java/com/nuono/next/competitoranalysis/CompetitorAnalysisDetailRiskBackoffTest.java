package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CompetitorAnalysisDetailRiskBackoffTest {

    @Test
    void scheduledDetailMonitoringRecordsLaterRiskFailureInsteadOfFirstOrdinaryFailure() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        CompetitorMonitoringMapper monitoringMapper = mock(CompetitorMonitoringMapper.class);
        CompetitorKeywordRefreshTransactionRunner keywordRunner =
                mock(CompetitorKeywordRefreshTransactionRunner.class);
        CompetitorProductDetailRefreshService detailService =
                mock(CompetitorProductDetailRefreshService.class);
        NoonRiskBackoffRepository riskRepository = mock(NoonRiskBackoffRepository.class);
        List<Runnable> submittedTasks = new ArrayList<>();
        Clock clock = Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC);
        InMemoryOperationalTaskRepository taskRepository =
                new InMemoryOperationalTaskRepository();
        OperationalTaskService taskService =
                new OperationalTaskService(taskRepository, clock);
        Map<Long, CompetitorSearchRunRow> persistedRuns = new LinkedHashMap<>();
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CompetitorSearchRunInsertCommand command = invocation.getArgument(0);
            persistedRuns.put(command.getTaskId(), searchRun(command));
            return null;
        }).when(mapper).insertSearchRun(any());
        org.mockito.Mockito.lenient().when(mapper.selectSearchRunByTaskId(
                org.mockito.ArgumentMatchers.anyLong()
        )).thenAnswer(invocation -> persistedRuns.get(invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(mapper.markSearchRunRunning(
                org.mockito.ArgumentMatchers.anyLong()
        )).thenReturn(1);
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
        CompetitorProductDetailRefreshResult detailResult = detailFailureWithLaterRisk();
        CompetitorMonitoringBoundaryRow boundary = new CompetitorMonitoringBoundaryRow();
        boundary.setEligibleTotal(1L);
        boundary.setUpperWatchProductId(180123L);
        when(monitoringMapper.selectRefreshableWatchProductBoundary(
                501L, "STR108065-NSA", "SA"
        )).thenReturn(boundary);
        when(monitoringMapper.listRefreshableWatchProducts(
                eq(501L),
                eq("STR108065-NSA"),
                eq("SA"),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(180123L),
                org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(List.of(watchProduct), List.of());
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct);
        when(detailService.refreshConfirmedCompetitors(
                eq(watchProduct),
                eq(220123L),
                eq(150001L),
                isNull()
        )).thenReturn(detailResult);

        CompetitorTaskView batch =
                service.requestScheduledDetailMonitoring(501L, "STR108065-NSA", "SA");
        submittedTasks.get(0).run();
        submittedTasks.get(1).run();

        OperationalTask productTask = taskRepository.selectById(batch.getTaskId() + 1);
        assertTrue(productTask.getStatus() == OperationalTaskStatus.FAILED);
        assertTrue("COMPETITOR_RISK_BACKOFF".equals(productTask.getErrorCode()));
        assertTrue(productTask.getMessage().contains("rate_limited"));
        verify(mapper).completeRunningRefreshRun(
                eq(productTask.getId()),
                eq(220123L),
                eq(180123L),
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

    private static CompetitorSearchRunRow searchRun(
            CompetitorSearchRunInsertCommand command
    ) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(command.getId());
        row.setWatchProductId(command.getWatchProductId());
        row.setTaskId(command.getTaskId());
        row.setTriggerMode(command.getTriggerMode());
        row.setStatus(command.getStatus());
        row.setRequestedBy(command.getRequestedBy());
        row.setKeywordTotal(command.getKeywordTotal());
        return row;
    }
}
