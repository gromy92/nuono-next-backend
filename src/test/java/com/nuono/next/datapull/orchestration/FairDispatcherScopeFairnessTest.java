package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderLease;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FairDispatcherScopeFairnessTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 3, 0);
    private static final DataPullRuntimeLeaderLease LEADER = new DataPullRuntimeLeaderLease(
            "worker-1", 9L, NOW.plusMinutes(1), NOW
    );

    @Test
    void oneBusyScopeCannotPushAnotherScopeBehindItsWholeCandidateQueue() {
        DataPullTaskStore store = mock(DataPullTaskStore.class);
        List<DataPullTask> candidates = List.of(
                candidate(1L, "account-a", "scope-busy", "busy-1"),
                candidate(2L, "account-a", "scope-busy", "busy-2"),
                candidate(3L, "account-a", "scope-busy", "busy-3"),
                candidate(4L, "account-a", "scope-small", "small-1"),
                candidate(5L, "account-b", "scope-peer", "peer-1")
        );
        Map<Long, DataPullTask> candidatesById = Map.of(
                1L, candidates.get(0),
                2L, candidates.get(1),
                3L, candidates.get(2),
                4L, candidates.get(3),
                5L, candidates.get(4)
        );
        when(store.dueCandidatesAfter(eq(NOW), isNull(), isNull(), eq(64)))
                .thenReturn(candidates);
        when(store.claim(
                anyLong(), anyLong(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class),
                any(DataPullRuntimeLeaderLease.class)
        )).thenAnswer(invocation -> Optional.of(
                candidatesById.get(invocation.getArgument(0, Long.class))
        ));
        FairDispatcher dispatcher = new FairDispatcher(
                store,
                (level, identity, now) -> false,
                new InMemoryEmergencyClaimHoldStore()
        );

        List<DataPullTask> claimed = dispatcher.dispatchDue(
                NOW, 3, Duration.ofMinutes(5), LEADER
        );

        assertEquals(
                List.of("scope-busy", "scope-small", "scope-peer"),
                List.of(
                        claimed.get(0).getScopeKey(),
                        claimed.get(1).getScopeKey(),
                        claimed.get(2).getScopeKey()
                )
        );
    }

    @Test
    void stopAfterFirstClaimReturnsThatPartialClaimInsteadOfLosingItsLease() {
        DataPullTaskStore store = mock(DataPullTaskStore.class);
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        List<DataPullTask> candidates = List.of(
                candidate(11L, "account-a", "scope-a", "window-a"),
                candidate(12L, "account-b", "scope-b", "window-b")
        );
        when(store.dueCandidatesAfter(eq(NOW), isNull(), isNull(), eq(64)))
                .thenReturn(candidates);
        when(store.claim(
                anyLong(), anyLong(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class),
                any(DataPullRuntimeLeaderLease.class)
        )).thenAnswer(invocation -> {
            stopSignal.markStopping();
            return Optional.of(candidates.get(0));
        });
        FairDispatcher dispatcher = new FairDispatcher(
                store,
                (level, identity, now) -> false,
                new InMemoryEmergencyClaimHoldStore()
        );

        List<DataPullTask> claimed;
        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(5), stopSignal)) {
            claimed = dispatcher.dispatchDue(NOW, 2, Duration.ofMinutes(5), LEADER);
        }

        assertEquals(1, claimed.size());
        assertEquals(11L, claimed.get(0).getId());
        verify(store, times(1)).claim(
                anyLong(), anyLong(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class),
                any(DataPullRuntimeLeaderLease.class)
        );
        assertTrue(Thread.interrupted());
    }

    private DataPullTask candidate(long id, String account, String scope, String window) {
        return DataPullTask.queued(
                id,
                OperationCode.DP04,
                "noon-partner",
                307L,
                108065L,
                account,
                null,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                scope,
                NOW,
                window,
                "FETCH",
                NOW.minusMinutes(1)
        );
    }
}
