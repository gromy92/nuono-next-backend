package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompetitorMonitoringBatchServiceTest {
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
    void manualStoreBatchAttemptsProduct501AndAdvancesPastOnePoisonProduct() {
        List<CompetitorWatchProductRow> products = products(501L, 1L, 502L);
        stubProductPages(products);
        when(mapper.selectRefreshableWatchProductBoundary(501L, "STORE", "SA"))
                .thenReturn(boundary(501L, 501L), boundary(502L, 502L));
        List<Long> attempted = new ArrayList<>();
        CompetitorMonitoringBatchService service = service((product, actor, batchKey) -> {
            attempted.add(product.getId());
            if (product.getId() == 250L) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "COMPETITOR_NO_ACTIVE_KEYWORD"
                );
            }
            return CompetitorMonitoringEnqueueOutcome.CREATED;
        });

        CompetitorTaskView view = service.requestStore(501L, "STORE", "SA", 601L);

        assertEquals(OperationalTaskStatus.QUEUED, repository.selectById(view.getTaskId()).getStatus());
        submitted.get(0).run();

        OperationalTask completed = repository.selectById(view.getTaskId());
        CompetitorMonitoringCheckpoint result = CompetitorMonitoringCheckpoint.fromJson(completed.getResultJson());
        assertEquals(501, attempted.size());
        assertEquals(501, new HashSet<>(attempted).size());
        assertTrue(attempted.contains(501L));
        assertTrue(!attempted.contains(502L));
        assertEquals(500L, result.getNewlyQueued());
        assertEquals(1L, result.getFailed());
        assertEquals(501L, result.getEligibleSeen());
        assertTrue(result.isCompleted());
        assertEquals(OperationalTaskStatus.SUCCEEDED, completed.getStatus());
        verify(mapper).selectRefreshableWatchProductBoundary(501L, "STORE", "SA");
    }

    @Test
    void staleStoreBatchResumesAfterPersistedProductCursorOnlyOnce() {
        CompetitorMonitoringCheckpoint checkpoint = new CompetitorMonitoringCheckpoint();
        checkpoint.setBatchKind("STORE");
        checkpoint.setBatchKey("batch-501");
        checkpoint.setTriggerMode(CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR.triggerMode());
        checkpoint.setExecutionMode(CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR.taskKey());
        checkpoint.setCurrentOwnerUserId(501L);
        checkpoint.setCurrentStoreCode("STORE");
        checkpoint.setCurrentSiteCode("SA");
        checkpoint.setUpperWatchProductId(501L);
        checkpoint.setEligibleProductTotal(501L);
        checkpoint.setAfterWatchProductId(500L);
        checkpoint.setEligibleSeen(500L);
        checkpoint.setNewlyQueued(500L);
        OperationalTask stale = staleTask(checkpoint);
        repository.insert(stale);
        stubProductPages(List.of(product(501L, 501L)));
        List<Long> attempted = new ArrayList<>();
        CompetitorMonitoringBatchService service = service((product, actor, batchKey) -> {
            attempted.add(product.getId());
            assertEquals("batch-501", batchKey);
            return CompetitorMonitoringEnqueueOutcome.CREATED;
        });

        assertEquals(1, service.recoverStaleManualBatches());
        submitted.get(0).run();
        assertEquals(List.of(501L), attempted);
        assertEquals(OperationalTaskStatus.FAILED, repository.selectById(150000L).getStatus());
        assertEquals(OperationalTaskStatus.SUCCEEDED, repository.selectById(150001L).getStatus());
        assertEquals(0, service.recoverStaleManualBatches());
        assertEquals(List.of(501L), attempted);
    }

    @Test
    void queuedBatchIsNotMisclassifiedAsStaleWhileWaitingForItsAccountQueue() {
        CompetitorMonitoringCheckpoint checkpoint = new CompetitorMonitoringCheckpoint();
        checkpoint.setBatchKind("STORE");
        checkpoint.setBatchKey("waiting");
        checkpoint.setTriggerMode(CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR.triggerMode());
        OperationalTask queued = staleTask(checkpoint);
        queued.setStatus(OperationalTaskStatus.QUEUED);
        repository.insert(queued);

        CompetitorMonitoringBatchService service = service((product, actor, batchKey) ->
                CompetitorMonitoringEnqueueOutcome.CREATED
        );

        assertEquals(0, service.recoverStaleManualBatches());
        assertEquals(OperationalTaskStatus.QUEUED, repository.selectById(150000L).getStatus());
        assertTrue(submitted.isEmpty());
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

    private void stubProductPages(List<CompetitorWatchProductRow> products) {
        when(mapper.listRefreshableWatchProducts(
                anyLong(), any(), any(), anyLong(), anyLong(), anyInt()
        )).thenAnswer(invocation -> {
            long owner = invocation.getArgument(0);
            long afterId = invocation.getArgument(3);
            long upperId = invocation.getArgument(4);
            int limit = invocation.getArgument(5);
            return products.stream()
                    .filter(product -> product.getOwnerUserId() == owner)
                    .filter(product -> product.getId() > afterId && product.getId() <= upperId)
                    .limit(limit)
                    .collect(Collectors.toList());
        });
    }

    private static List<CompetitorWatchProductRow> products(long owner, long first, long last) {
        return LongStream.rangeClosed(first, last)
                .mapToObj(id -> product(id, owner))
                .collect(Collectors.toList());
    }

    private static CompetitorWatchProductRow product(long id, long owner) {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(id);
        row.setOwnerUserId(owner);
        row.setStoreCode("STORE" + (owner == 501L ? "" : "-" + (owner - 500L)));
        row.setSiteCode("SA");
        return row;
    }

    private static CompetitorMonitoringBoundaryRow boundary(long total, long upperId) {
        CompetitorMonitoringBoundaryRow row = new CompetitorMonitoringBoundaryRow();
        row.setEligibleTotal(total);
        row.setUpperWatchProductId(upperId);
        return row;
    }

    private static OperationalTask staleTask(CompetitorMonitoringCheckpoint checkpoint) {
        OperationalTask task = new OperationalTask();
        task.setId(150000L);
        task.setTaskType(CompetitorMonitoringBatchService.STORE_TASK_TYPE);
        task.setNaturalKey("store:501:STORE:SA:full-monitor");
        task.setOwnerUserId(501L);
        task.setStoreCode("STORE");
        task.setSiteCode("SA");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setPayloadJson(checkpoint.toJson());
        task.setProgressPercent(90);
        task.setStartedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        task.setCreatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        task.setUpdatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        return task;
    }
}
