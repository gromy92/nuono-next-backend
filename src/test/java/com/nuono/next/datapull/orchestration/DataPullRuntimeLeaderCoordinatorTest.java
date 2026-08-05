package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import com.nuono.next.datapull.leader.DataPullRuntimeLeaderStore;
import com.nuono.next.datapull.leader.DataPullRuntimeLeadership;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class DataPullRuntimeLeaderCoordinatorTest {

    @Test
    void followerNeverReconcilesOrClaims() {
        @SuppressWarnings("unchecked")
        DataPullRuntimeReconciler reconcile = mock(DataPullRuntimeReconciler.class);
        DataPullRuntimeCoordinator.DispatchAction dispatch = mock(
                DataPullRuntimeCoordinator.DispatchAction.class
        );
        RuntimeExecutor executor = mock(RuntimeExecutor.class);
        DataPullRuntimeLeadership follower = new DataPullRuntimeLeadership(
                new NeverLeaderStore(), "dp:follower", Duration.ofSeconds(120)
        );
        DataPullRuntimeCoordinator coordinator = new DataPullRuntimeCoordinator(
                reconcile, dispatch, executor, List.of(), directExecutor(),
                Clock.fixed(Instant.parse("2026-08-03T04:00:00Z"), ZoneOffset.UTC),
                follower, Duration.ofMinutes(5), 1, 1
        );

        assertEquals(0, coordinator.tick().getReconciledTasks());
        assertEquals(0, coordinator.dispatchAvailable());
        verify(reconcile, never()).reconcileAt(org.mockito.ArgumentMatchers.any());
        verify(dispatch, never()).dispatchDue(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private Executor directExecutor() { return Runnable::run; }

    private static final class NeverLeaderStore implements DataPullRuntimeLeaderStore {
        @Override
        public Optional<DataPullRuntimeLeaderLease> acquireOrRenew(
                String owner,
                Duration leaseDuration
        ) {
            return Optional.empty();
        }

        @Override public boolean isCurrent(DataPullRuntimeLeaderLease lease) { return false; }
        @Override public boolean release(DataPullRuntimeLeaderLease lease) { return false; }
    }
}
