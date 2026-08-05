package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BackoffHoldStoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 6, 30);

    @Test
    void exactHoldDoesNotLeakToAnotherScope() {
        InMemoryBackoffHoldStore store = new InMemoryBackoffHoldStore();
        DataPullBackoffIdentity first = identity("account-a", "scope-a", "exit-1");
        DataPullBackoffIdentity second = identity("account-a", "scope-b", "exit-1");

        store.record(RiskShareLevel.EXACT, first, NOW.plusMinutes(5), "HTTP_429", NOW);

        assertTrue(store.isHeld(RiskShareLevel.EXACT, first, NOW.plusMinutes(1)));
        assertFalse(store.isHeld(RiskShareLevel.EXACT, second, NOW.plusMinutes(1)));
        assertFalse(store.isHeld(RiskShareLevel.EXACT, first, NOW.plusMinutes(5)));
    }

    @Test
    void provenAccountAndExitKeysShareOnlyTheirVerifiedIdentity() {
        DataPullBackoffIdentity first = identity("account-a", "scope-a", "exit-1");
        DataPullBackoffIdentity sameAccount = identity("account-a", "scope-b", "exit-2");
        DataPullBackoffIdentity sameExit = identity("account-b", "scope-c", "exit-1");

        assertTrue(DataPullBackoffHoldKey.from(RiskShareLevel.ACCOUNT, first).equals(
                DataPullBackoffHoldKey.from(RiskShareLevel.ACCOUNT, sameAccount)
        ));
        assertTrue(DataPullBackoffHoldKey.from(RiskShareLevel.EXIT, first).equals(
                DataPullBackoffHoldKey.from(RiskShareLevel.EXIT, sameExit)
        ));
        assertNotEquals(
                DataPullBackoffHoldKey.from(RiskShareLevel.EXACT, first),
                DataPullBackoffHoldKey.from(RiskShareLevel.EXACT, sameAccount)
        );
    }

    @Test
    void holdCanOnlyExtendAndExitRequiresVerifiedKey() {
        InMemoryBackoffHoldStore store = new InMemoryBackoffHoldStore();
        DataPullBackoffIdentity identity = identity("account-a", "scope-a", "exit-1");
        store.record(RiskShareLevel.EXACT, identity, NOW.plusMinutes(10), "HTTP_429", NOW);
        store.record(RiskShareLevel.EXACT, identity, NOW.plusMinutes(2), "HTTP_403", NOW);

        assertTrue(store.isHeld(RiskShareLevel.EXACT, identity, NOW.plusMinutes(9)));
        assertThrows(
                IllegalStateException.class,
                () -> DataPullBackoffHoldKey.from(
                        RiskShareLevel.EXIT,
                        identity("account-a", "scope-a", null)
                )
        );
    }

    private DataPullBackoffIdentity identity(String account, String scope, String egress) {
        DataPullTask task = DataPullTask.queued(
                1L,
                OperationCode.DP06,
                "noon-ads",
                307L,
                108065L,
                account,
                egress,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                scope,
                NOW,
                "2026-08-01",
                "FETCH",
                NOW
        );
        return DataPullBackoffIdentity.from(task);
    }
}
