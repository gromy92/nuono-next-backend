package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;

@ExtendWith(MockitoExtension.class)
class CompetitorMonitoringInfrastructureRecoveryTest {
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
    void databaseFailureKeepsCursorBeforeTheItemAndRecoveryRetriesIt() {
        when(mapper.selectRefreshableWatchProductBoundary(501L, "STORE", "SA"))
                .thenReturn(boundary(2L, 2L));
        stubProductPages(List.of(product(1L), product(2L)));
        AtomicBoolean failOnce = new AtomicBoolean(true);
        List<Long> attempted = new ArrayList<>();
        CompetitorMonitoringBatchService service = service((product, actor, mode, batchKey) -> {
            attempted.add(product.getId());
            if (failOnce.getAndSet(false)) {
                throw new TransientDataAccessResourceException("database unavailable");
            }
            return CompetitorMonitoringEnqueueOutcome.CREATED;
        });

        CompetitorTaskView view = service.requestStore(
                501L, "STORE", "SA", 601L, CompetitorRefreshExecutionMode.FULL_MANUAL_MONITOR
        );
        submitted.remove(0).run();

        OperationalTask parked = repository.selectById(view.getTaskId());
        CompetitorMonitoringCheckpoint parkedAt = CompetitorMonitoringCheckpoint.fromJson(
                parked.getPayloadJson()
        );
        assertEquals(OperationalTaskStatus.RUNNING, parked.getStatus());
        assertEquals(0L, parkedAt.getAfterWatchProductId());
        repository.tasks.get(view.getTaskId()).setUpdatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));

        assertEquals(1, service.recoverStaleBatches());
        submitted.remove(0).run();

        OperationalTask replacement = repository.selectById(view.getTaskId() + 1L);
        assertEquals(List.of(1L, 1L, 2L), attempted);
        assertEquals(OperationalTaskStatus.FAILED, repository.selectById(view.getTaskId()).getStatus());
        assertEquals(OperationalTaskStatus.SUCCEEDED, replacement.getStatus());
    }

    @Test
    void staleCycleResumesCurrentScopeAndProductExactlyOnce() {
        CompetitorMonitoringCheckpoint checkpoint = new CompetitorMonitoringCheckpoint();
        checkpoint.setBatchKind("CYCLE");
        checkpoint.setBatchKey("cycle:scheduled-rank:2026-07-28T06");
        checkpoint.setTriggerMode(CompetitorRefreshExecutionMode.SCHEDULED_RANK.triggerMode());
        checkpoint.setCurrentOwnerUserId(501L);
        checkpoint.setCurrentStoreCode("STORE");
        checkpoint.setCurrentSiteCode("SA");
        checkpoint.setAfterWatchProductId(500L);
        checkpoint.setUpperWatchProductId(501L);
        checkpoint.setUpperScopeOwnerUserId(501L);
        checkpoint.setUpperScopeStoreCode("STORE");
        checkpoint.setUpperScopeSiteCode("SA");
        checkpoint.setCompletedScopeCount(100L);
        repository.insert(staleCycle(checkpoint));
        stubProductPages(List.of(product(501L)));
        when(mapper.listRefreshableWatchProductScopes(
                anyLong(), any(), any(), any(), anyLong(), any(), any(), anyInt()
        )).thenReturn(List.of());
        List<Long> attempted = new ArrayList<>();
        CompetitorMonitoringBatchService service = service((product, actor, mode, batchKey) -> {
            attempted.add(product.getId());
            assertEquals(checkpoint.getBatchKey(), batchKey);
            return CompetitorMonitoringEnqueueOutcome.CREATED;
        });

        assertEquals(1, service.recoverStaleBatches());

        OperationalTask replacement = repository.selectById(150001L);
        CompetitorMonitoringCheckpoint result = CompetitorMonitoringCheckpoint.fromJson(
                replacement.getResultJson()
        );
        assertEquals(List.of(501L), attempted);
        assertEquals(101L, result.getCompletedScopeCount());
        assertTrue(result.isCompleted());
        assertEquals(OperationalTaskStatus.FAILED, repository.selectById(150000L).getStatus());
        assertEquals(OperationalTaskStatus.SUCCEEDED, replacement.getStatus());
        assertEquals(0, service.recoverStaleBatches());
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
            long afterId = invocation.getArgument(3);
            long upperId = invocation.getArgument(4);
            int limit = invocation.getArgument(5);
            return products.stream()
                    .filter(product -> product.getId() > afterId && product.getId() <= upperId)
                    .limit(limit)
                    .collect(java.util.stream.Collectors.toList());
        });
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

    private static OperationalTask staleCycle(CompetitorMonitoringCheckpoint checkpoint) {
        OperationalTask task = new OperationalTask();
        task.setId(150000L);
        task.setTaskType(CompetitorMonitoringBatchService.CYCLE_TASK_TYPE);
        task.setNaturalKey(checkpoint.getBatchKey());
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setPayloadJson(checkpoint.toJson());
        task.setStartedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        task.setCreatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        task.setUpdatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        return task;
    }
}
