package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffRepository;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorRefreshRiskPartialTerminalTest {
    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private CompetitorMonitoringMapper monitoringMapper;
    @Mock private CompetitorKeywordRefreshTransactionRunner keywordRunner;
    @Mock private CompetitorProductDetailRefreshService detailService;
    @Mock private NoonRiskBackoffRepository riskRepository;

    @Test
    void partialDetailSuccessWithRiskFailureEndsTaskFailedRunPartialFailed() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-28T01:00:00Z"),
                ZoneOffset.UTC
        );
        InMemoryOperationalTaskRepository tasks =
                new InMemoryOperationalTaskRepository();
        List<Runnable> submitted = new ArrayList<>();
        AtomicReference<CompetitorSearchRunRow> persistedRun =
                new AtomicReference<>();
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CompetitorSearchRunInsertCommand command =
                    invocation.getArgument(0);
            persistedRun.set(run(command));
            return null;
        }).when(mapper).insertSearchRun(any());
        org.mockito.Mockito.lenient().when(
                mapper.selectSearchRunByTaskId(anyLong())
        ).thenAnswer(ignored -> persistedRun.get());
        org.mockito.Mockito.lenient().when(mapper.selectSearchRunById(anyLong()))
                .thenAnswer(invocation -> {
                    CompetitorSearchRunRow run = persistedRun.get();
                    return run != null
                            && invocation.getArgument(0).equals(run.getId())
                            ? run
                            : null;
                });
        when(mapper.markSearchRunRunning(220123L)).thenReturn(1);

        CompetitorAnalysisRefreshService service =
                new CompetitorAnalysisRefreshService(
                        mapper,
                        monitoringMapper,
                        new OperationalTaskService(tasks, clock),
                        (accountKey, task) -> submitted.add(task),
                        keywordRunner,
                        detailService,
                        clock,
                        new NoonRiskBackoffGuard(riskRepository, clock)
                );
        CompetitorWatchProductRow watchProduct = watchProduct();
        when(mapper.selectWatchProductById(501L, 180123L))
                .thenReturn(watchProduct);
        when(mapper.listActiveKeywordsByWatchProductId(180123L))
                .thenReturn(List.of(keyword()));
        when(keywordRunner.runKeyword(
                anyLong(), anyLong(), eq(watchProduct), any(), eq(601L)
        )).thenReturn(CompetitorKeywordRefreshResult.success(0, 0));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectWatchProductForRefresh(180123L))
                .thenReturn(watchProduct);
        CompetitorProductDetailRefreshResult detailResult =
                partialRiskResult();
        when(detailService.refreshConfirmedCompetitors(
                eq(watchProduct), eq(220123L), anyLong(), eq(601L)
        )).thenReturn(detailResult);

        CompetitorRefreshRunView view =
                service.requestRefresh(operatorContext(), 180123L);
        submitted.get(0).run();

        OperationalTask task = tasks.selectById(view.getTaskId());
        assertEquals(OperationalTaskStatus.FAILED, task.getStatus());
        assertEquals("COMPETITOR_RISK_BACKOFF", task.getErrorCode());
        verify(mapper).completeRunningRefreshRun(
                eq(view.getTaskId()),
                eq(220123L),
                eq(180123L),
                eq("PARTIAL_FAILED"),
                eq(1),
                eq(0),
                anyInt(),
                anyInt(),
                eq("RATE_LIMITED"),
                eq("HTTP 429"),
                eq(601L)
        );
        verify(mapper).updateLatestRefreshRunIfNotOlder(
                180123L, 220123L, "PARTIAL_FAILED", 601L
        );
        verify(keywordRunner).runKeyword(
                anyLong(), anyLong(), eq(watchProduct), any(), eq(601L)
        );
    }

    private static CompetitorProductDetailRefreshResult partialRiskResult() {
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        CompetitorProductDetailTarget succeeded =
                CompetitorProductDetailTarget.self("ZSELF001");
        CompetitorProductDetailTarget failed =
                CompetitorProductDetailTarget.competitor(
                        88002L, "ZRISK001", null
                );
        result.recordAttempt(succeeded);
        result.recordSuccess(succeeded);
        result.recordAttempt(failed);
        result.recordFailure(failed, "RATE_LIMITED", "HTTP 429");
        return result;
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("BASKET-SA-001");
        row.setSelfNoonProductCode("ZSELF001");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorKeywordRow keyword() {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(190001L);
        row.setWatchProductId(180123L);
        row.setKeyword("basket");
        row.setKeywordNorm("basket");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorSearchRunRow run(
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

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 501L))
                .menuPaths(Set.of("/operations/competitor-analysis"))
                .build();
    }
}
