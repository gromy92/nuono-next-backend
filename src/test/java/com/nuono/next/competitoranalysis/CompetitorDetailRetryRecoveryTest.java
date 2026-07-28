package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorDetailRetryRecoveryTest {
    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private CompetitorMonitoringMapper monitoringMapper;
    @Mock private CompetitorKeywordRefreshTransactionRunner keywordRunner;
    @Mock private CompetitorProductDetailRefreshService detailService;

    private MutableClock clock;
    private InMemoryOperationalTaskRepository taskRepository;
    private List<Runnable> submitted;
    private Map<Long, CompetitorSearchRunRow> runsByTask;
    private CompetitorAnalysisRefreshService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-06-06T08:00:00Z"));
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
        org.mockito.Mockito.lenient().when(mapper.selectSearchRunById(anyLong()))
                .thenAnswer(invocation -> runsByTask.values().stream()
                        .filter(run -> run.getId().equals(invocation.getArgument(0)))
                        .findFirst().orElse(null));
        org.mockito.Mockito.lenient().when(mapper.markSearchRunRunning(anyLong())).thenReturn(1);
        org.mockito.Mockito.lenient().when(mapper.requeueRunningRefreshRun(
                anyLong(), anyLong(), anyLong(), any(), any()
        ))
                .thenReturn(1);
    }

    @Test
    void retrySurvivesQueueAndFetchesOnlyFailedProductAfterBackoff() {
        CompetitorWatchProductRow product = product();
        CompetitorProductDetailTarget succeeded =
                CompetitorProductDetailTarget.self("ZSELF001");
        CompetitorProductDetailTarget failed =
                CompetitorProductDetailTarget.competitor(
                        88002L,
                        "ZFAIL002",
                        "https://www.noon.com/saudi-en/zfail002/p"
                );
        CompetitorProductDetailRefreshResult firstAttempt =
                CompetitorProductDetailRefreshResult.empty();
        firstAttempt.recordAttempt(succeeded);
        firstAttempt.recordSuccess(succeeded);
        firstAttempt.recordAttempt(failed);
        firstAttempt.recordFailure(failed, "DETAIL_REFRESH_FAILED", "detail timeout");
        CompetitorProductDetailRefreshResult retryAttempt =
                CompetitorProductDetailRefreshResult.empty();
        retryAttempt.recordAttempt(failed);
        retryAttempt.recordSuccess(failed);

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
        when(detailService.refreshConfirmedCompetitors(
                eq(product), eq(220123L), anyLong(), org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(firstAttempt);
        when(detailService.refreshTargets(
                eq(product),
                eq(List.of(failed)),
                eq(220123L),
                anyLong(),
                org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(retryAttempt);

        CompetitorTaskView parent =
                service.requestScheduledDetailMonitoring(501L, "STORE", "SA");
        submitted.get(0).run();
        submitted.get(1).run();

        Long productTaskId = parent.getTaskId() + 1L;
        assertEquals(
                OperationalTaskStatus.QUEUED,
                taskRepository.selectById(productTaskId).getStatus()
        );
        assertTrue(taskRepository.selectById(productTaskId).getPayloadJson()
                .contains("\"retryAttempt\":1"));
        assertEquals(0, service.resumeQueuedRefreshTasks());

        clock.advanceSeconds(120L);
        assertEquals(1, service.resumeQueuedRefreshTasks());
        submitted.get(2).run();

        assertEquals(
                OperationalTaskStatus.SUCCEEDED,
                taskRepository.selectById(productTaskId).getStatus()
        );
        assertTrue(taskRepository.selectById(productTaskId).getResultJson()
                .contains("\"detailAttempted\":2"));
        assertTrue(taskRepository.selectById(productTaskId).getResultJson()
                .contains("\"detailRequestAttempts\":3"));
        assertTrue(taskRepository.selectById(productTaskId).getResultJson()
                .contains("\"detailSuccess\":2"));
        verify(detailService, times(1)).refreshConfirmedCompetitors(
                eq(product), eq(220123L), eq(productTaskId), any()
        );
        verify(detailService, times(1)).refreshTargets(
                eq(product), eq(List.of(failed)), eq(220123L), eq(productTaskId), any()
        );
        verify(detailService, never()).refreshTargets(
                eq(product), eq(List.of(succeeded)), anyLong(), anyLong(), any()
        );
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
        row.setPartnerSku("DETAIL");
        row.setSelfNoonProductCode("ZSELF001");
        return row;
    }

    private static CompetitorSearchRunRow run(CompetitorSearchRunInsertCommand command) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(command.getId());
        row.setTaskId(command.getTaskId());
        row.setWatchProductId(command.getWatchProductId());
        row.setRequestedBy(command.getRequestedBy());
        row.setTriggerMode(command.getTriggerMode());
        row.setStatus("QUEUED");
        return row;
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private MutableClock(Instant instant) { this.instant = new AtomicReference<>(instant); }
        private void advanceSeconds(long seconds) {
            instant.updateAndGet(value -> value.plusSeconds(seconds));
        }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
