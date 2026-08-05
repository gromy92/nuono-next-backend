package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.DataPullScopeAdmissionStore;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ScheduleReconcilerRuntimePortTest {

    @Test
    void observesTheExactOutcomeBeforeReturningItsCount() {
        AtomicReference<ScheduleReconciliationOutcome> observed = new AtomicReference<>();
        AtomicReference<Instant> observedAt = new AtomicReference<>();
        ScheduleReconciler reconciler = new ScheduleReconciler(
                new DataPullScheduleRegistry(),
                new DataPullJobRegistry(List.of()),
                new InMemoryDataPullTaskStore(),
                DataPullScheduleAnchorStore.failClosed(),
                DataPullScopeAdmissionStore.failClosed(),
                java.util.function.Supplier::get,
                null,
                (outcome, timestamp) -> {
                    observed.set(outcome);
                    observedAt.set(timestamp);
                }
        );
        Instant now = Instant.parse("2026-08-02T04:00:00Z");

        int reconciled = reconciler.reconcileAt(now);

        assertEquals(0, reconciled);
        assertEquals(0, observed.get().getReconciledTaskCount());
        assertSame(now, observedAt.get());
    }
}
