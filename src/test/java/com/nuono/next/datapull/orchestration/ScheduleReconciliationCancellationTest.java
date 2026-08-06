package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.DataPullScopeAdmissionStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleReconciliationCancellationTest {

    private static final Instant NOW = Instant.parse("2026-08-03T04:00:00Z");

    @Test
    void expiredDeadlineEscapesWithoutBecomingAnOperationFailure() {
        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofNanos(1))) {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> reconciler().reconcileOperations(NOW)
            );
            assertEquals("DP_ADVANCE_DEADLINE_EXCEEDED", failure.getMessage());
        }
    }

    @Test
    void shutdownCancellationEscapesWithoutBecomingAnOperationFailure() {
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        try (DataPullAdvanceDeadline ignored = DataPullAdvanceDeadline.open(
                Duration.ofSeconds(1), stopSignal
        )) {
            stopSignal.markStopping();
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> reconciler().reconcileOperations(NOW)
            );
            assertEquals("DP_ADVANCE_DEADLINE_EXCEEDED", failure.getMessage());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void plainInterruptEscapesBeforeAnyOperationIsReconciled() {
        Thread.currentThread().interrupt();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> reconciler().reconcileOperations(NOW)
            );
            assertEquals("DP_RUNTIME_INTERRUPTED", failure.getMessage());
        } finally {
            Thread.interrupted();
        }
    }

    private ScheduleReconciler reconciler() {
        TestDataPullJob job = new TestDataPullJob(
                OperationCode.DP04,
                "noon-product",
                List.of(),
                ignored -> AdvanceResult.succeeded()
        );
        return new ScheduleReconciler(
                new DataPullScheduleRegistry(),
                new DataPullJobRegistry(List.of(job)),
                new InMemoryDataPullTaskStore(),
                DataPullScheduleAnchorStore.failClosed(),
                DataPullScopeAdmissionStore.failClosed()
        );
    }
}
