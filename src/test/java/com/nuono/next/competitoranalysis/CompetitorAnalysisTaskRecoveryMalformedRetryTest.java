package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CompetitorAnalysisTaskRecoveryMalformedRetryTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC
    );

    @Test
    void malformedRetryDoesNotBlockLaterReadyTask() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        OperationalTaskService taskService = mock(OperationalTaskService.class);
        CompetitorRefreshTaskFactory taskFactory =
                mock(CompetitorRefreshTaskFactory.class);
        CompetitorDetailRetryCoordinator retryCoordinator =
                new CompetitorDetailRetryCoordinator(taskFactory, CLOCK);
        OperationalTask malformed = task(
                150001L,
                "{\"retryAttempt\":1,\"retryNotBefore\":\"not-a-date\"}"
        );
        OperationalTask valid = task(
                150002L,
                "{\"retryNotBefore\":\"2026-06-06T07:59:00\"}"
        );
        CompetitorSearchRunRow malformedRun = run(220001L);
        CompetitorSearchRunRow validRun = run(220002L);
        CompetitorWatchProductRow product = new CompetitorWatchProductRow();
        product.setId(180001L);
        AtomicBoolean validSubmitted = new AtomicBoolean();
        CompetitorAnalysisTaskRecovery recovery = new CompetitorAnalysisTaskRecovery(
                mapper,
                taskService,
                CLOCK,
                (task, run, watchProduct) -> {
                    if (!retryCoordinator.isReady(task)) {
                        return false;
                    }
                    validSubmitted.set(task.getId().equals(valid.getId()));
                    return true;
                },
                (task, watchProduct, run, staleBefore) -> false
        );
        when(taskService.listActiveAfter(
                CompetitorAnalysisRefreshService.TASK_TYPE, 0L, 1000
        )).thenReturn(List.of(malformed, valid));
        when(taskService.listActiveAfter(
                CompetitorAnalysisRefreshService.TASK_TYPE, 150002L, 1000
        )).thenReturn(List.of());
        when(mapper.selectSearchRunByTaskId(150001L)).thenReturn(malformedRun);
        when(mapper.selectSearchRunByTaskId(150002L)).thenReturn(validRun);
        when(mapper.selectWatchProductForRefresh(180001L)).thenReturn(product);

        assertDoesNotThrow(recovery::resumeQueuedRefreshTasks);
        assertTrue(validSubmitted.get());
    }

    private static OperationalTask task(long id, String payloadJson) {
        OperationalTask task = new OperationalTask();
        task.setId(id);
        task.setStatus(OperationalTaskStatus.QUEUED);
        task.setPayloadJson(payloadJson);
        task.setUpdatedAt(LocalDateTime.parse("2026-06-06T08:00:00"));
        return task;
    }

    private static CompetitorSearchRunRow run(long id) {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(id);
        run.setWatchProductId(180001L);
        run.setStatus("QUEUED");
        run.setTriggerMode("SCHEDULED_RANK_MONITOR");
        return run;
    }
}
