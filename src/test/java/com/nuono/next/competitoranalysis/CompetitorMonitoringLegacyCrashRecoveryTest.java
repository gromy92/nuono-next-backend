package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorMonitoringMapper;
import com.nuono.next.infrastructure.mapper.OperationalTaskMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorMonitoringLegacyCrashRecoveryTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T03:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private CompetitorMonitoringMapper monitoringMapper;
    @Mock
    private CompetitorAnalysisMapper analysisMapper;

    private InMemoryOperationalTaskRepository repository;
    private OperationalTaskService taskService;
    private List<Runnable> submitted;
    private Map<Long, CompetitorSearchRunRow> runsByTask;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOperationalTaskRepository();
        taskService = new OperationalTaskService(repository, CLOCK);
        submitted = new ArrayList<>();
        runsByTask = new LinkedHashMap<>();
        org.mockito.Mockito.lenient().when(analysisMapper.selectSearchRunByTaskId(anyLong()))
                .thenAnswer(invocation -> runsByTask.get(invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(analysisMapper.nextSearchRunId()).thenReturn(220000L);
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CompetitorSearchRunInsertCommand command = invocation.getArgument(0);
            runsByTask.put(command.getTaskId(), run(command));
            return 1;
        }).when(analysisMapper).insertSearchRun(any());
    }

    @Test
    void crashAfterChildInsertKeepsPersistedBatchIdentityAndFrozenBoundary() {
        repository.insert(queuedLegacyParent());
        when(monitoringMapper.selectRefreshableWatchProductBoundary(501L, "STORE", "SA"))
                .thenReturn(boundary(1L, 1L), boundary(2L, 2L));
        List<CompetitorWatchProductRow> products = List.of(product(1L), product(2L));
        when(monitoringMapper.listRefreshableWatchProducts(
                anyLong(), any(), any(), anyLong(), anyLong(), anyInt()
        )).thenAnswer(invocation -> page(
                products,
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5)
        ));
        CompetitorRefreshTaskFactory childFactory = new CompetitorRefreshTaskFactory(
                analysisMapper,
                taskService
        );
        AtomicBoolean crashOnce = new AtomicBoolean(true);
        List<String> attemptedBatchKeys = new ArrayList<>();
        List<CompetitorMonitoringEnqueueOutcome> outcomes = new ArrayList<>();
        CompetitorMonitoringBatchService service = service((product, actor, mode, batchKey) -> {
            CompetitorQueuedRefresh child = childFactory.persistQueued(
                    product,
                    actor,
                    mode,
                    "watchProduct:" + product.getId() + ":" + mode.taskKey(),
                    batchKey,
                    0
            );
            attemptedBatchKeys.add(batchKey);
            outcomes.add(child.getOutcome());
            if (crashOnce.getAndSet(false)) {
                throw new SimulatedProcessCrash();
            }
            return child.getOutcome();
        });

        assertEquals(1, service.resumeQueuedBatches());
        assertThrows(SimulatedProcessCrash.class, submitted.remove(0)::run);

        OperationalTask interrupted = repository.selectById(150000L);
        CompetitorMonitoringCheckpoint persisted = CompetitorMonitoringCheckpoint.fromJson(
                interrupted.getPayloadJson()
        );
        assertEquals(OperationalTaskStatus.RUNNING, interrupted.getStatus());
        assertEquals("STORE", persisted.getBatchKind());
        assertEquals(1L, persisted.getUpperWatchProductId());
        assertTrue(persisted.getBatchKey() != null && !persisted.getBatchKey().isBlank());
        repository.tasks.get(150000L).setUpdatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));

        assertEquals(1, service.recoverStaleBatches());
        submitted.remove(0).run();

        OperationalTask replacement = repository.selectById(150002L);
        CompetitorMonitoringCheckpoint result = CompetitorMonitoringCheckpoint.fromJson(
                replacement.getResultJson()
        );
        assertEquals(OperationalTaskStatus.SUCCEEDED, replacement.getStatus());
        assertEquals(List.of(persisted.getBatchKey(), persisted.getBatchKey()), attemptedBatchKeys);
        assertEquals(
                List.of(
                        CompetitorMonitoringEnqueueOutcome.CREATED,
                        CompetitorMonitoringEnqueueOutcome.REUSED_SAME_BATCH
                ),
                outcomes
        );
        assertEquals(1L, result.getUpperWatchProductId());
        assertEquals(1L, result.getAlreadyAttempted());
        assertEquals(0L, result.getNewlyQueued());
        verify(monitoringMapper, times(1))
                .selectRefreshableWatchProductBoundary(501L, "STORE", "SA");
        verify(analysisMapper, times(1)).insertSearchRun(any());
    }

    @Test
    void productionQueuedPayloadUpgradeRequiresExpectedPayloadAndQueuedStatus() {
        Method method = java.util.Arrays.stream(OperationalTaskMapper.class.getMethods())
                .filter(candidate -> "compareAndSetQueuedPayload".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("WHERE id = #{taskId}"));
        assertTrue(sql.contains("status = 'QUEUED'"));
        assertTrue(sql.contains("payload_json <=> #{expectedPayloadJson}"));
    }

    private CompetitorMonitoringBatchService service(
            CompetitorMonitoringBatchRunner.ProductEnqueuer enqueuer
    ) {
        return new CompetitorMonitoringBatchService(
                monitoringMapper,
                taskService,
                new CompetitorMonitoringRecoveryService(taskService),
                (accountKey, task) -> submitted.add(task),
                enqueuer,
                () -> { },
                CLOCK
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

    private static OperationalTask queuedLegacyParent() {
        OperationalTask task = new OperationalTask();
        task.setId(150000L);
        task.setTaskType(CompetitorMonitoringBatchService.STORE_TASK_TYPE);
        task.setNaturalKey("store:501:STORE:SA");
        task.setOwnerUserId(501L);
        task.setStoreCode("STORE");
        task.setSiteCode("SA");
        task.setStatus(OperationalTaskStatus.QUEUED);
        task.setPayloadJson(
                "{\"triggerMode\":\"MANUAL_MONITOR\",\"executionMode\":\"full-monitor\","
                        + "\"rankRefresh\":true,\"detailRefresh\":true,\"watchProductTotal\":1}"
        );
        task.setCreatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        task.setUpdatedAt(LocalDateTime.parse("2026-07-28T01:00:00"));
        return task;
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

    private static CompetitorSearchRunRow run(CompetitorSearchRunInsertCommand command) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(command.getId());
        row.setTaskId(command.getTaskId());
        row.setWatchProductId(command.getWatchProductId());
        row.setTriggerMode(command.getTriggerMode());
        row.setStatus(command.getStatus());
        return row;
    }

    private static final class SimulatedProcessCrash extends Error {
    }
}
