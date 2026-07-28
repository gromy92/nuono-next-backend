package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffRepository;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
class CompetitorRiskQueuedRecoveryTest {
    @Mock
    private CompetitorAnalysisMapper mapper;
    @Mock
    private CompetitorMonitoringMapper monitoringMapper;
    @Mock
    private CompetitorKeywordRefreshTransactionRunner keywordRunner;

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
        NoonRiskBackoffGuard guard = new NoonRiskBackoffGuard(new RiskRepository(), clock);
        service = new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(taskRepository, clock),
                (accountKey, task) -> submitted.add(task),
                keywordRunner,
                null,
                clock,
                guard
        );
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CompetitorSearchRunInsertCommand command = invocation.getArgument(0);
            runsByTask.put(command.getTaskId(), run(command));
            return 1;
        }).when(mapper).insertSearchRun(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.lenient().when(mapper.selectSearchRunByTaskId(anyLong()))
                .thenAnswer(invocation -> runsByTask.get(invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(mapper.markSearchRunRunning(anyLong())).thenReturn(1);
    }

    @Test
    void queuedRiskPausedProductRunsOnceAfterTheHoldExpires() {
        CompetitorWatchProductRow first = product(180123L, "FIRST");
        CompetitorWatchProductRow second = product(180124L, "SECOND");
        CompetitorKeywordRow firstKeyword = keyword(190001L, 180123L);
        CompetitorKeywordRow secondKeyword = keyword(190002L, 180124L);
        when(monitoringMapper.selectRefreshableWatchProductBoundary(501L, "STORE", "SA"))
                .thenReturn(boundary());
        when(monitoringMapper.listRefreshableWatchProducts(
                anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                anyLong(),
                anyLong(),
                anyInt()
        )).thenAnswer(invocation -> page(
                List.of(first, second),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5)
        ));
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(firstKeyword));
        when(mapper.listActiveKeywordsByWatchProductId(180124L)).thenReturn(List.of(secondKeyword));
        when(mapper.nextSearchRunId()).thenReturn(220123L, 220124L);
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(first);
        when(mapper.selectWatchProductForRefresh(180124L)).thenReturn(second);
        when(keywordRunner.runKeyword(
                anyLong(), org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(first),
                org.mockito.ArgumentMatchers.eq(firstKeyword),
                org.mockito.ArgumentMatchers.isNull()
        ))
                .thenReturn(CompetitorKeywordRefreshResult.failure("RATE_LIMITED", "HTTP 429"));
        when(keywordRunner.runKeyword(
                anyLong(), org.mockito.ArgumentMatchers.eq(220124L),
                org.mockito.ArgumentMatchers.eq(second),
                org.mockito.ArgumentMatchers.eq(secondKeyword),
                org.mockito.ArgumentMatchers.isNull()
        ))
                .thenReturn(CompetitorKeywordRefreshResult.success(1, 1));

        CompetitorTaskView parent = service.requestScheduledRankMonitoring(501L, "STORE", "SA");
        submitted.get(0).run();
        submitted.get(1).run();
        submitted.get(2).run();

        Long pausedTaskId = parent.getTaskId() + 2L;
        assertEquals(OperationalTaskStatus.QUEUED, taskRepository.selectById(pausedTaskId).getStatus());
        verify(keywordRunner, never()).runKeyword(
                anyLong(), org.mockito.ArgumentMatchers.eq(220124L),
                org.mockito.ArgumentMatchers.eq(second),
                org.mockito.ArgumentMatchers.eq(secondKeyword),
                org.mockito.ArgumentMatchers.isNull()
        );

        clock.advanceSeconds(3600L);
        assertEquals(1, service.resumeQueuedRefreshTasks());
        submitted.get(3).run();

        assertEquals(OperationalTaskStatus.SUCCEEDED, taskRepository.selectById(pausedTaskId).getStatus());
        verify(keywordRunner, times(1)).runKeyword(
                anyLong(), org.mockito.ArgumentMatchers.eq(220124L),
                org.mockito.ArgumentMatchers.eq(second),
                org.mockito.ArgumentMatchers.eq(secondKeyword),
                org.mockito.ArgumentMatchers.isNull()
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
        row.setEligibleTotal(2L);
        row.setUpperWatchProductId(180124L);
        return row;
    }

    private static CompetitorWatchProductRow product(long id, String sku) {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(id);
        row.setOwnerUserId(501L);
        row.setStoreCode("STORE");
        row.setSiteCode("SA");
        row.setPartnerSku(sku);
        return row;
    }

    private static CompetitorKeywordRow keyword(long id, long watchProductId) {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(id);
        row.setWatchProductId(watchProductId);
        row.setKeyword("basket-" + id);
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

    private static final class RiskRepository implements NoonRiskBackoffRepository {
        private final Map<String, NoonRiskBackoffHold> holds = new LinkedHashMap<>();

        @Override
        public void upsert(NoonRiskBackoffHold hold) {
            holds.put(hold.getScopeKey(), hold.copy());
        }

        @Override
        public NoonRiskBackoffHold selectActiveHold(String scopeKey, LocalDateTime now) {
            NoonRiskBackoffHold hold = holds.get(scopeKey);
            return hold != null && hold.getBlockedUntil().isAfter(now) ? hold.copy() : null;
        }

        @Override
        public NoonRiskBackoffHold selectLatestHold(String scopeKey) {
            NoonRiskBackoffHold hold = holds.get(scopeKey);
            return hold == null ? null : hold.copy();
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        private void advanceSeconds(long seconds) {
            instant.updateAndGet(value -> value.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
