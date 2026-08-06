package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        CompetitorMonitoringBatchService service = service((product, actor, batchKey) -> {
            attempted.add(product.getId());
            if (failOnce.getAndSet(false)) {
                throw new TransientDataAccessResourceException("database unavailable");
            }
            return CompetitorMonitoringEnqueueOutcome.CREATED;
        });

        CompetitorTaskView view = service.requestStore(501L, "STORE", "SA", 601L);
        submitted.remove(0).run();

        OperationalTask parked = repository.selectById(view.getTaskId());
        CompetitorMonitoringCheckpoint parkedAt = CompetitorMonitoringCheckpoint.fromJson(
                parked.getPayloadJson()
        );
        assertEquals(OperationalTaskStatus.RUNNING, parked.getStatus());
        assertEquals(0L, parkedAt.getAfterWatchProductId());
        repository.tasks.get(view.getTaskId()).setUpdatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));

        assertEquals(1, service.recoverStaleManualBatches());
        submitted.remove(0).run();

        OperationalTask replacement = repository.selectById(view.getTaskId() + 1L);
        assertEquals(List.of(1L, 1L, 2L), attempted);
        assertEquals(OperationalTaskStatus.FAILED, repository.selectById(view.getTaskId()).getStatus());
        assertEquals(OperationalTaskStatus.SUCCEEDED, replacement.getStatus());
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

}
