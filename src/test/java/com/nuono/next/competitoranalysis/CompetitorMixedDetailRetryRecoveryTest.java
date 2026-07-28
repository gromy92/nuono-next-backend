package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.system.task.OperationalTask;
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
class CompetitorMixedDetailRetryRecoveryTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private CompetitorMonitoringMapper monitoringMapper;
    @Mock private CompetitorKeywordRefreshTransactionRunner keywordRunner;
    @Mock private CompetitorProductDetailRefreshService detailService;

    private MutableClock clock;
    private InMemoryOperationalTaskRepository taskRepository;
    private List<Runnable> submitted;
    private Map<Long, CompetitorSearchRunRow> runsByTask;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-06-06T08:00:00Z"));
        taskRepository = new InMemoryOperationalTaskRepository();
        submitted = new ArrayList<>();
        runsByTask = new LinkedHashMap<>();
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CompetitorSearchRunInsertCommand command = invocation.getArgument(0);
            runsByTask.put(command.getTaskId(), run(command));
            return 1;
        }).when(mapper).insertSearchRun(any());
        org.mockito.Mockito.lenient().when(mapper.selectSearchRunByTaskId(anyLong()))
                .thenAnswer(invocation -> runsByTask.get(invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(mapper.selectSearchRunById(anyLong()))
                .thenAnswer(invocation -> runsByTask.values().stream()
                        .filter(run -> invocation.getArgument(0).equals(run.getId()))
                        .findFirst()
                        .orElse(null));
        org.mockito.Mockito.lenient().when(mapper.markSearchRunRunning(anyLong())).thenReturn(1);
        org.mockito.Mockito.lenient().when(mapper.requeueSearchRun(
                anyLong(), anyLong(), anyLong(), any(), any()
        ))
                .thenReturn(1);
    }

    @Test
    void restartRunsReadyOrdinaryTargetAndKeepsNotFoundTargetUntilItsOwnWakeTime()
            throws Exception {
        CompetitorWatchProductRow product = product();
        CompetitorProductDetailTarget ordinary =
                CompetitorProductDetailTarget.competitor(88002L, "ZORDINARY", null);
        CompetitorProductDetailTarget notFound =
                CompetitorProductDetailTarget.competitor(88003L, "ZNOTFOUND", null);
        CompetitorProductDetailRefreshResult mixedFailure =
                CompetitorProductDetailRefreshResult.empty();
        mixedFailure.recordAttempt(ordinary);
        mixedFailure.recordFailure(ordinary, "DETAIL_REFRESH_FAILED", "timeout");
        mixedFailure.recordAttempt(notFound);
        mixedFailure.recordFailure(
                notFound,
                "PUBLIC_DETAIL_NOT_FOUND",
                "not found"
        );
        CompetitorProductDetailRefreshResult ordinaryRecovered = success(ordinary);
        CompetitorProductDetailRefreshResult notFoundRecovered = success(notFound);

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
        when(detailService.currentTargets(product))
                .thenReturn(List.of(ordinary, notFound));
        stubDetailRefresh(product, List.of(ordinary, notFound), mixedFailure);
        stubDetailRefresh(product, List.of(ordinary), ordinaryRecovered);
        stubDetailRefresh(product, List.of(notFound), notFoundRecovered);

        CompetitorAnalysisRefreshService service = service();
        CompetitorTaskView parent =
                service.requestScheduledDetailMonitoring(501L, "STORE", "SA");
        submitted.get(0).run();
        submitted.get(1).run();

        Long taskId = parent.getTaskId() + 1L;
        OperationalTask queued = taskRepository.selectById(taskId);
        assertEquals(OperationalTaskStatus.QUEUED, queued.getStatus());
        assertRetryWakes(
                queued,
                "2026-06-06T08:30:00",
                List.of("2026-06-06T08:02:00", "2026-06-06T08:30:00")
        );

        submitted.clear();
        service = service();
        clock.advanceSeconds(120L);
        assertEquals(1, service.resumeQueuedRefreshTasks());
        submitted.get(0).run();

        queued = taskRepository.selectById(taskId);
        assertEquals(OperationalTaskStatus.QUEUED, queued.getStatus());
        assertRetryWakes(
                queued,
                "2026-06-06T08:30:00",
                List.of("2026-06-06T08:30:00")
        );
        verify(detailService).refreshTargets(
                eq(product),
                eq(List.of(ordinary)),
                eq(220123L),
                eq(taskId),
                isNull(),
                any(CompetitorDetailRetrySession.class),
                any(Runnable.class)
        );

        submitted.clear();
        service = service();
        clock.advanceSeconds(28L * 60L);
        assertEquals(1, service.resumeQueuedRefreshTasks());
        submitted.get(0).run();

        OperationalTask completed = taskRepository.selectById(taskId);
        assertEquals(OperationalTaskStatus.SUCCEEDED, completed.getStatus());
        assertTrue(completed.getResultJson().contains("\"detailRequestAttempts\":4"));
        assertTrue(completed.getResultJson().contains("\"detailSuccess\":2"));
        verify(detailService).refreshTargets(
                eq(product),
                eq(List.of(notFound)),
                eq(220123L),
                eq(taskId),
                isNull(),
                any(CompetitorDetailRetrySession.class),
                any(Runnable.class)
        );
        verify(detailService, times(1)).refreshTargets(
                eq(product),
                eq(List.of(ordinary, notFound)),
                eq(220123L),
                eq(taskId),
                isNull(),
                any(CompetitorDetailRetrySession.class),
                any(Runnable.class)
        );
    }

    private void stubDetailRefresh(
            CompetitorWatchProductRow product,
            List<CompetitorProductDetailTarget> targets,
            CompetitorProductDetailRefreshResult result
    ) {
        when(detailService.refreshTargets(
                eq(product), eq(targets), eq(220123L), anyLong(), isNull(),
                any(CompetitorDetailRetrySession.class), any(Runnable.class)
        )).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(6).run();
            return CompetitorDetailRetryMockSupport
                    .checkpointing(taskRepository, result)
                    .answer(invocation);
        });
    }

    private CompetitorAnalysisRefreshService service() {
        return new CompetitorAnalysisRefreshService(
                mapper,
                monitoringMapper,
                new OperationalTaskService(taskRepository, clock),
                (accountKey, task) -> submitted.add(task),
                keywordRunner,
                detailService,
                clock,
                NoonRiskBackoffGuard.disabled()
        );
    }

    private static void assertRetryWakes(
            OperationalTask task,
            String expectedWake,
            List<String> expectedStateWakes
    ) throws Exception {
        JsonNode payload = JSON.readTree(task.getPayloadJson());
        assertEquals(expectedWake, payload.path("retryNotBefore").asText());
        assertEquals(expectedStateWakes.size(), payload.path("detailRetryStates").size());
        for (int index = 0; index < expectedStateWakes.size(); index++) {
            assertEquals(
                    expectedStateWakes.get(index),
                    payload.path("detailRetryStates").path(index)
                            .path("retryNotBefore").asText()
            );
        }
    }

    private static CompetitorProductDetailRefreshResult success(
            CompetitorProductDetailTarget target
    ) {
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        result.recordAttempt(target);
        result.recordSuccess(target);
        return result;
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
