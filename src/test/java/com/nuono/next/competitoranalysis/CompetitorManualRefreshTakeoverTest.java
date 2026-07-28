package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorManualRefreshTakeoverTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-06T08:00:00Z"),
            ZoneOffset.UTC
    );

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
    void manualTakeoverReusesTaskThatRenewedBeforeStaleCas() {
        OperationalTask staleSnapshot = runningTask("2026-06-06T07:20:00");
        OperationalTask renewed = runningTask("2026-06-06T07:59:50");
        CompetitorSearchRunRow run = runningRun();
        CompetitorWatchProductRow watchProduct = watchProduct();
        when(mapper.selectWatchProductById(501L, 180001L)).thenReturn(watchProduct);
        when(mapper.listActiveKeywordsByWatchProductId(180001L)).thenReturn(List.of(keyword()));
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(run);
        when(operationalTaskService.findActive(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                "watchProduct:180001"
        )).thenReturn(Optional.of(staleSnapshot), Optional.of(renewed));
        when(operationalTaskService.failStaleRunning(
                150001L,
                LocalDateTime.parse("2026-06-06T07:30:00"),
                "FAILED_STALE",
                "刷新任务超过 30 分钟未完成，已自动释放。"
        )).thenReturn(false);
        CompetitorRefreshTaskFactory taskFactory =
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
                taskFactory
        );

        CompetitorRefreshRunView result = service.requestRefresh(context(), 180001L);

        assertEquals(150001L, result.getTaskId());
        assertEquals(220001L, result.getRunId());
        verify(operationalTaskService, never()).fail(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(operationalTaskService, never()).queue(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                any()
        );
        verify(taskSubmitter, never()).submit(any(), any());
    }

    private static OperationalTask runningTask(String updatedAt) {
        OperationalTask task = new OperationalTask();
        task.setId(150001L);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:180001");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setUpdatedAt(LocalDateTime.parse(updatedAt));
        return task;
    }

    private static CompetitorSearchRunRow runningRun() {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(220001L);
        run.setTaskId(150001L);
        run.setWatchProductId(180001L);
        run.setStatus("RUNNING");
        run.setTriggerMode("MANUAL");
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
