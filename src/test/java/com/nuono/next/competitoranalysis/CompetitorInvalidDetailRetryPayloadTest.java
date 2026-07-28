package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CompetitorInvalidDetailRetryPayloadTest {
    @Test
    void malformedRetryPayloadTerminatesTaskAndRunInsteadOfOccupyingActiveSlot() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T02:00:00Z"), ZoneOffset.UTC);
        OperationalTaskService taskService = new OperationalTaskService(
                new InMemoryOperationalTaskRepository(),
                clock
        );
        OperationalTask task = taskService.queue(
                CompetitorAnalysisRefreshService.TASK_TYPE,
                "watchProduct:180123:SCHEDULED_DETAIL_MONITOR:2026-07-28",
                OperationalTaskPayload.builder()
                        .payloadJson("{\"retryNotBefore\":\"not-a-date\"}")
                        .build()
        );
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(220123L);
        when(mapper.selectSearchRunByTaskId(task.getId())).thenReturn(run);
        when(mapper.markSearchRunFailed(
                220123L,
                "INVALID_DETAIL_RETRY_PAYLOAD",
                "竞品详情重试载荷损坏，任务已终止以避免阻塞恢复队列。"
        )).thenReturn(1);
        CompetitorDetailRetryCoordinator coordinator = new CompetitorDetailRetryCoordinator(
                new CompetitorRefreshTaskFactory(mapper, taskService),
                clock
        );

        assertFalse(coordinator.isReady(task));

        OperationalTask failed = taskService.find(task.getId()).orElseThrow();
        assertEquals(OperationalTaskStatus.FAILED, failed.getStatus());
        assertEquals("INVALID_DETAIL_RETRY_PAYLOAD", failed.getErrorCode());
        verify(mapper).markSearchRunFailed(
                220123L,
                "INVALID_DETAIL_RETRY_PAYLOAD",
                "竞品详情重试载荷损坏，任务已终止以避免阻塞恢复队列。"
        );
    }
}
