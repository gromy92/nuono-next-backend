package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationalTaskRequeueTest {
    private OperationalTaskService service;

    @BeforeEach
    void setUp() {
        service = new OperationalTaskService(
                new InMemoryOperationalTaskRepository(),
                Clock.fixed(Instant.parse("2026-06-04T05:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void atomicallyRequeuesRunningTaskWithRetryPayload() {
        OperationalTask queued = service.queue(
                "operations.competitor.refresh",
                "watch-product:180123:detail",
                OperationalTaskPayload.empty()
        );
        assertTrue(service.claimQueued(queued.getId(), "running detail"));
        OperationalTask running = service.find(queued.getId()).orElseThrow();

        assertTrue(service.requeueRunning(
                queued.getId(),
                " {\"retryAttempt\":1,\"retryNotBefore\":\"2026-06-04T05:02:00\"} ",
                37,
                " DETAIL_FETCH_FAILED ",
                " retry after backoff "
        ));

        OperationalTask requeued = service.find(queued.getId()).orElseThrow();
        assertEquals(OperationalTaskStatus.QUEUED, requeued.getStatus());
        assertEquals(
                "{\"retryAttempt\":1,\"retryNotBefore\":\"2026-06-04T05:02:00\"}",
                requeued.getPayloadJson()
        );
        assertEquals(37, requeued.getProgressPercent());
        assertEquals("DETAIL_FETCH_FAILED", requeued.getErrorCode());
        assertEquals("retry after backoff", requeued.getMessage());
        assertEquals(running.getStartedAt(), requeued.getStartedAt());
        assertNull(requeued.getFinishedAt());
        assertFalse(service.requeueRunning(
                queued.getId(),
                "{\"retryAttempt\":2}",
                50,
                "DETAIL_FETCH_FAILED",
                "duplicate retry"
        ));
    }

    @Test
    void rejectsRequeueWhenTaskIsNotRunning() {
        OperationalTask queued = service.queue(
                "operations.competitor.refresh",
                "watch-product:180123:detail",
                OperationalTaskPayload.empty()
        );

        assertFalse(service.requeueRunning(
                queued.getId(),
                "{\"retryAttempt\":1}",
                101,
                "DETAIL_FETCH_FAILED",
                "retry after backoff"
        ));
        assertEquals(
                OperationalTaskStatus.QUEUED,
                service.find(queued.getId()).orElseThrow().getStatus()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.requeueRunning(null, null, null, null, null)
        );
    }
}
