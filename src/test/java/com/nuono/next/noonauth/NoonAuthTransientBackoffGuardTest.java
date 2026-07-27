package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonAuthTransientBackoffGuardTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-25T05:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void sameStoreKeepsIndependentAtomicCountersForThreeExactErrorTypes() {
        InMemoryRepository repository = new InMemoryRepository();
        NoonAuthTransientBackoffGuard guard =
                new NoonAuthTransientBackoffGuard(repository, CLOCK);
        NoonAuthRecoveryProjectTarget store = target(307L, "PRJ307", "STR307-NAE");
        NoonAuthRecoveryProjectTarget otherStore = target(308L, "PRJ308", "STR308-NAE");

        guard.recordFailure(
                store,
                7001L,
                fence(177L),
                NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                NoonTransientErrorType.NETWORK_EOF,
                "catalog eof"
        );
        guard.recordFailure(
                store,
                7001L,
                fence(177L),
                NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                NoonTransientErrorType.HTTP_500,
                "catalog 500"
        );
        guard.recordFailure(
                store,
                7001L,
                fence(177L),
                NoonAuthRecoveryFailureStage.WHOAMI_VALIDATION,
                NoonTransientErrorType.CONNECT_TIMEOUT,
                "whoami connect timeout"
        );
        NoonAuthTransientBackoffState secondEof = guard.recordFailure(
                target(307L, "PRJ307", "STR307-NSA"),
                7001L,
                fence(177L),
                NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                NoonTransientErrorType.NETWORK_EOF,
                "catalog eof again"
        );
        guard.recordFailure(
                otherStore,
                8001L,
                fence(178L),
                NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                NoonTransientErrorType.NETWORK_EOF,
                "other store eof"
        );

        assertEquals(4, repository.states.size());
        assertEquals(2, secondEof.getAttemptCount());
        assertEquals(LocalDateTime.of(2026, 7, 25, 5, 4), secondEof.getBlockedUntil());
        assertEquals(
                NoonTransientErrorType.NETWORK_EOF,
                guard.currentHold(7001L).orElseThrow().getErrorType()
        );
        assertTrue(guard.hasFailureForRecovery(7001L, 177L));
        assertTrue(guard.hasFailureForRecovery(8001L, 178L));
    }

    @Test
    void successResetsOnlyRowsStillOwnedByTheSameRecovery() {
        InMemoryRepository repository = new InMemoryRepository();
        NoonAuthTransientBackoffGuard guard =
                new NoonAuthTransientBackoffGuard(repository, CLOCK);
        NoonAuthRecoveryProjectTarget store = target(307L, "PRJ307", "STR307-NAE");

        guard.recordFailure(
                store,
                7001L,
                fence(177L),
                NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                NoonTransientErrorType.NETWORK_EOF,
                "first recovery"
        );
        guard.recordFailure(
                store,
                7001L,
                fence(177L),
                NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                NoonTransientErrorType.HTTP_503,
                "first recovery"
        );
        guard.recordFailure(
                store,
                7001L,
                fence(178L),
                NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                NoonTransientErrorType.NETWORK_EOF,
                "newer recovery"
        );

        assertTrue(guard.recordSuccess(7001L, fence(177L)));

        assertEquals(
                2,
                repository.state(7001L, NoonTransientErrorType.NETWORK_EOF).getAttemptCount()
        );
        assertEquals(
                0,
                repository.state(7001L, NoonTransientErrorType.HTTP_503).getAttemptCount()
        );
        assertTrue(guard.hasFailureForRecovery(7001L, 178L));
        assertTrue(!guard.hasFailureForRecovery(7001L, 177L));
    }

    @Test
    void backoffSequenceCapsAtSixteenMinutesAndResetRestartsAtTwo() {
        InMemoryRepository repository = new InMemoryRepository();
        NoonAuthTransientBackoffGuard guard =
                new NoonAuthTransientBackoffGuard(repository, CLOCK);
        NoonAuthRecoveryProjectTarget store = target(307L, "PRJ307", "STR307-NAE");

        List<Integer> minutes = new ArrayList<>();
        for (int attempt = 0; attempt < 5; attempt++) {
            NoonAuthTransientBackoffState state = guard.recordFailure(
                    store,
                    7001L,
                    fence(177L),
                    NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                    NoonTransientErrorType.NETWORK_EOF,
                    "catalog eof"
            );
            minutes.add((int) java.time.Duration.between(
                    state.getLastFailedAt(),
                    state.getBlockedUntil()
            ).toMinutes());
        }
        assertEquals(List.of(2, 4, 8, 16, 16), minutes);

        assertTrue(guard.recordSuccess(7001L, fence(177L)));
        NoonAuthTransientBackoffState restarted = guard.recordFailure(
                store,
                7001L,
                fence(178L),
                NoonAuthRecoveryFailureStage.CATALOG_VALIDATION,
                NoonTransientErrorType.NETWORK_EOF,
                "catalog eof after success"
        );
        assertEquals(1, restarted.getAttemptCount());
        assertEquals(
                LocalDateTime.of(2026, 7, 25, 5, 2),
                restarted.getBlockedUntil()
        );
    }

    private NoonAuthRecoveryProjectTarget target(
            Long ownerUserId,
            String projectCode,
            String storeCode
    ) {
        return new NoonAuthRecoveryProjectTarget(ownerUserId, projectCode, storeCode, 0L);
    }

    private NoonAuthTransientBackoffWriteFence fence(Long recoveryId) {
        return new NoonAuthTransientBackoffWriteFence(
                recoveryId,
                NoonAuthRecoveryStatus.AUTHENTICATING,
                3L,
                "lease-" + recoveryId
        );
    }

    private static final class InMemoryRepository
            implements NoonAuthTransientBackoffRepository {

        private final Map<String, NoonAuthTransientBackoffState> states = new LinkedHashMap<>();

        @Override
        public Long resolveLogicalStoreId(Long ownerUserId, String projectCode) {
            return ownerUserId == 307L ? 7001L : 8001L;
        }

        @Override
        public NoonAuthTransientBackoffState incrementFailure(
                NoonAuthTransientBackoffState failure,
                NoonAuthTransientBackoffWriteFence fence,
                LocalDateTime now
        ) {
            if (!failure.getSourceRecoveryId().equals(fence.getRecoveryId())) {
                return null;
            }
            String key = key(failure.getLogicalStoreId(), failure.getErrorType());
            NoonAuthTransientBackoffState state = states.get(key);
            int attempt = state == null || state.getAttemptCount() == null
                    ? 1
                    : state.getAttemptCount() + 1;
            if (state == null) {
                state = failure;
                states.put(key, state);
            }
            state.setAttemptCount(attempt);
            state.setBlockedUntil(failure.getLastFailedAt().plusMinutes(backoffMinutes(attempt)));
            state.setLastFailedAt(failure.getLastFailedAt());
            state.setSourceRecoveryId(failure.getSourceRecoveryId());
            state.setSourceStage(failure.getSourceStage());
            return state;
        }

        @Override
        public NoonAuthTransientBackoffState selectState(
                Long logicalStoreId,
                NoonTransientErrorType errorType
        ) {
            return state(logicalStoreId, errorType);
        }

        @Override
        public List<NoonAuthTransientBackoffState> listActiveHolds(
                Long logicalStoreId,
                LocalDateTime now
        ) {
            List<NoonAuthTransientBackoffState> holds = new ArrayList<>();
            for (NoonAuthTransientBackoffState state : states.values()) {
                if (logicalStoreId.equals(state.getLogicalStoreId())
                        && state.getAttemptCount() > 0
                        && state.getBlockedUntil().isAfter(now)) {
                    holds.add(state);
                }
            }
            return holds;
        }

        @Override
        public boolean hasFailureForRecovery(Long logicalStoreId, Long recoveryId) {
            return states.values().stream().anyMatch(state ->
                    logicalStoreId.equals(state.getLogicalStoreId())
                            && recoveryId.equals(state.getSourceRecoveryId())
                            && state.getAttemptCount() > 0
            );
        }

        @Override
        public boolean resetForRecovery(
                Long logicalStoreId,
                Long recoveryId,
                NoonAuthTransientBackoffWriteFence fence,
                LocalDateTime resetAt
        ) {
            for (NoonAuthTransientBackoffState state : states.values()) {
                if (logicalStoreId.equals(state.getLogicalStoreId())
                        && recoveryId.equals(state.getSourceRecoveryId())
                        && state.getAttemptCount() > 0) {
                    state.setAttemptCount(0);
                    state.setBlockedUntil(resetAt);
                    state.setLastSuccessAt(resetAt);
                }
            }
            return recoveryId.equals(fence.getRecoveryId());
        }

        private NoonAuthTransientBackoffState state(
                Long logicalStoreId,
                NoonTransientErrorType errorType
        ) {
            return states.get(key(logicalStoreId, errorType));
        }

        private String key(Long logicalStoreId, NoonTransientErrorType errorType) {
            return logicalStoreId + ":" + errorType.name();
        }

        private int backoffMinutes(int attempt) {
            return Math.min(16, 2 << Math.min(3, Math.max(0, attempt - 1)));
        }
    }
}
