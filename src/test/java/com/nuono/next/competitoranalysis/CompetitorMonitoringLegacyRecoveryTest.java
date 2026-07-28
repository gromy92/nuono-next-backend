package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorMonitoringLegacyRecoveryTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T03:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private CompetitorMonitoringMapper mapper;

    private InMemoryOperationalTaskRepository repository;
    private OperationalTaskService taskService;
    private List<Runnable> submitted;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOperationalTaskRepository();
        taskService = new OperationalTaskService(repository, CLOCK);
        submitted = new ArrayList<>();
    }

    @Test
    void manualStoreRequestReusesThePrePaginationNaturalKeyDuringDeployment() {
        OperationalTask active = legacyTask();
        active.setUpdatedAt(LocalDateTime.parse("2026-07-28T02:55:00"));
        repository.insert(active);
        CompetitorMonitoringBatchService service = service((product, actor, mode, batchKey) ->
                CompetitorMonitoringEnqueueOutcome.CREATED
        );

        CompetitorTaskView view = service.requestStore(
                501L, "STORE", "SA", 601L, CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR
        );

        assertEquals(active.getId(), view.getTaskId());
        assertTrue(submitted.isEmpty());
    }

    @Test
    void stalePrePaginationStoreTaskIsReplannedAndFullyEnumerated() {
        repository.insert(legacyTask());
        when(mapper.selectRefreshableWatchProductBoundary(501L, "STORE", "SA"))
                .thenReturn(boundary(2L, 2L));
        when(mapper.listRefreshableWatchProducts(
                anyLong(), any(), any(), anyLong(), anyLong(), anyInt()
        )).thenAnswer(invocation -> {
            long afterId = invocation.getArgument(3);
            return afterId == 0L ? List.of(product(1L), product(2L)) : List.of();
        });
        List<Long> attempted = new ArrayList<>();
        CompetitorMonitoringBatchService service = service((product, actor, mode, batchKey) -> {
            attempted.add(product.getId());
            assertEquals(CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR, mode);
            assertTrue(batchKey != null && !batchKey.isBlank());
            return CompetitorMonitoringEnqueueOutcome.CREATED;
        });

        assertEquals(1, service.recoverStaleBatches());
        submitted.get(0).run();

        assertEquals(List.of(1L, 2L), attempted);
        assertEquals(OperationalTaskStatus.FAILED, repository.selectById(150000L).getStatus());
        OperationalTask replacement = repository.selectById(150001L);
        assertEquals(OperationalTaskStatus.SUCCEEDED, replacement.getStatus());
        CompetitorMonitoringCheckpoint result = CompetitorMonitoringCheckpoint.fromJson(
                replacement.getResultJson()
        );
        assertEquals("STORE", result.getBatchKind());
        assertEquals(2L, result.getEligibleSeen());
    }

    @Test
    void queuedPrePaginationStoreTaskIsReplannedAndFullyEnumerated() {
        OperationalTask queued = legacyTask();
        queued.setStatus(OperationalTaskStatus.QUEUED);
        queued.setPayloadJson(legacyPayload(500L));
        repository.insert(queued);
        List<CompetitorWatchProductRow> products = LongStream.rangeClosed(1L, 501L)
                .mapToObj(CompetitorMonitoringLegacyRecoveryTest::product)
                .collect(Collectors.toList());
        when(mapper.selectRefreshableWatchProductBoundary(501L, "STORE", "SA"))
                .thenReturn(boundary(501L, 501L));
        when(mapper.listRefreshableWatchProducts(
                anyLong(), any(), any(), anyLong(), anyLong(), anyInt()
        )).thenAnswer(invocation -> {
            long afterId = invocation.getArgument(3);
            long upperId = invocation.getArgument(4);
            int limit = invocation.getArgument(5);
            return products.stream()
                    .filter(product -> product.getId() > afterId && product.getId() <= upperId)
                    .limit(limit)
                    .collect(Collectors.toList());
        });
        List<Long> attempted = new ArrayList<>();
        CompetitorMonitoringBatchService service = service((product, actor, mode, batchKey) -> {
            attempted.add(product.getId());
            return CompetitorMonitoringEnqueueOutcome.CREATED;
        });

        assertEquals(1, service.resumeQueuedBatches());
        submitted.get(0).run();

        assertEquals(501, attempted.size());
        assertEquals(501, new HashSet<>(attempted).size());
        assertTrue(attempted.contains(501L));
        OperationalTask completed = repository.selectById(150000L);
        assertEquals(OperationalTaskStatus.SUCCEEDED, completed.getStatus());
        CompetitorMonitoringCheckpoint result = CompetitorMonitoringCheckpoint.fromJson(
                completed.getResultJson()
        );
        assertEquals("STORE", result.getBatchKind());
        assertEquals(501L, result.getEligibleProductTotal());
        assertEquals(501L, result.getEligibleSeen());
    }

    @Test
    void unknownBatchKindFailsClosedWithoutEnumeratingProducts() {
        CompetitorMonitoringCheckpoint checkpoint = new CompetitorMonitoringCheckpoint();
        checkpoint.setBatchKind("UNKNOWN");
        checkpoint.setBatchKey("unknown-kind");
        checkpoint.setTriggerMode(CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR.triggerMode());
        checkpoint.setExecutionMode(CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR.taskKey());
        checkpoint.setCurrentOwnerUserId(501L);
        checkpoint.setCurrentStoreCode("STORE");
        checkpoint.setCurrentSiteCode("SA");
        checkpoint.setUpperWatchProductId(2L);
        checkpoint.setEligibleProductTotal(2L);
        OperationalTask queued = legacyTask();
        queued.setStatus(OperationalTaskStatus.QUEUED);
        queued.setPayloadJson(checkpoint.toJson());
        repository.insert(queued);
        List<Long> attempted = new ArrayList<>();
        CompetitorMonitoringBatchService service = service((product, actor, mode, batchKey) -> {
            attempted.add(product.getId());
            return CompetitorMonitoringEnqueueOutcome.CREATED;
        });

        assertEquals(1, service.resumeQueuedBatches());
        submitted.get(0).run();

        OperationalTask failed = repository.selectById(150000L);
        assertEquals(OperationalTaskStatus.FAILED, failed.getStatus());
        assertEquals("COMPETITOR_MONITOR_FAILED", failed.getErrorCode());
        assertTrue(attempted.isEmpty());
        verify(mapper, never()).listRefreshableWatchProducts(
                anyLong(), any(), any(), anyLong(), anyLong(), anyInt()
        );
    }

    private CompetitorMonitoringBatchService service(
            CompetitorMonitoringBatchRunner.ProductEnqueuer enqueuer
    ) {
        return new CompetitorMonitoringBatchService(
                mapper,
                taskService,
                new CompetitorMonitoringRecoveryService(taskService),
                (accountKey, task) -> submitted.add(task),
                enqueuer,
                () -> { },
                CLOCK
        );
    }

    private static OperationalTask legacyTask() {
        OperationalTask task = new OperationalTask();
        task.setId(150000L);
        task.setTaskType(CompetitorMonitoringBatchService.STORE_TASK_TYPE);
        task.setNaturalKey("store:501:STORE:SA");
        task.setOwnerUserId(501L);
        task.setStoreCode("STORE");
        task.setSiteCode("SA");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setPayloadJson(legacyPayload(2L));
        task.setStartedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        task.setCreatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        task.setUpdatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        return task;
    }

    private static String legacyPayload(long watchProductTotal) {
        return "{\"triggerMode\":\"MANUAL_MONITOR\",\"executionMode\":\"full-monitor\","
                + "\"rankRefresh\":true,\"detailRefresh\":true,\"watchProductTotal\":"
                + watchProductTotal + "}";
    }

    private static CompetitorMonitoringBoundaryRow boundary(long total, long upperId) {
        CompetitorMonitoringBoundaryRow row = new CompetitorMonitoringBoundaryRow();
        row.setEligibleTotal(total);
        row.setUpperWatchProductId(upperId);
        return row;
    }

    private static CompetitorWatchProductRow product(long id) {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(id);
        row.setOwnerUserId(501L);
        row.setStoreCode("STORE");
        row.setSiteCode("SA");
        return row;
    }
}
