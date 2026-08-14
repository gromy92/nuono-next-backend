package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonDataPullAuthRecoveryQueueTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 3, 0);

    @Test
    void noonAuthFailureRequestsOneManualLoginWithoutCreatingARecoveryTask() {
        AtomicInteger manualLoginRequests = new AtomicInteger();
        NoonDataPullAuthRecoveryQueue queue = queue(manualLoginRequests);

        queue.enqueue(task(OperationCode.DP04), 8L, NOW);

        assertEquals(1, manualLoginRequests.get());
    }

    @Test
    void nonNoonOperationDoesNotRequestManualLogin() {
        AtomicInteger manualLoginRequests = new AtomicInteger();

        queue(manualLoginRequests).enqueue(task(OperationCode.DP10), 8L, NOW);

        assertEquals(0, manualLoginRequests.get());
    }

    @Test
    void rejectsAWaitingVersionThatDoesNotFollowTheClaimedSnapshot() {
        AtomicInteger manualLoginRequests = new AtomicInteger();

        assertThrows(
                IllegalArgumentException.class,
                () -> queue(manualLoginRequests).enqueue(task(OperationCode.DP04), 9L, NOW)
        );
        assertEquals(0, manualLoginRequests.get());
    }

    private NoonDataPullAuthRecoveryQueue queue(AtomicInteger manualLoginRequests) {
        return new NoonDataPullAuthRecoveryQueue(new NoonAccountSessionAttentionPort() {
            @Override
            public void requireManualLogin() {
                manualLoginRequests.incrementAndGet();
            }

            @Override
            public boolean blocksProviderCalls() {
                return true;
            }
        });
    }

    private DataPullTask task(OperationCode operationCode) {
        DataPullTask task = DataPullTask.queued(
                1L,
                operationCode,
                operationCode == OperationCode.DP10 ? "ALI1688_OPEN_API" : "NOON_PARTNER_PRODUCT_LIST",
                307L,
                108065L,
                "account-307",
                "egress-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-sa",
                NOW,
                operationCode.name() + ":2026-08-02",
                "FETCH",
                NOW.minusMinutes(1)
        );
        task.setVersion(7L);
        return task;
    }
}
