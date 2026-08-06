package com.nuono.next.datapull.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RuntimeCoreContractTest {

    @Test
    void operationCatalogContainsNoDp09() {
        assertArrayEquals(
                new OperationCode[]{
                        OperationCode.DP01,
                        OperationCode.DP02,
                        OperationCode.DP03,
                        OperationCode.DP04,
                        OperationCode.DP05,
                        OperationCode.DP06,
                        OperationCode.DP07A,
                        OperationCode.DP07B,
                        OperationCode.DP08A,
                        OperationCode.DP08B,
                        OperationCode.DP10
                },
                OperationCode.values()
        );
        assertThrows(IllegalArgumentException.class, () -> OperationCode.valueOf("DP09"));
    }

    @Test
    void advanceResultCannotKeepAWorkerRunningWhileWaiting() {
        AdvanceResult result = AdvanceResult.waitingBackoff(
                "page=7",
                Duration.ofMinutes(4),
                "HTTP_429"
        );

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals("page=7", result.getCheckpoint());
        assertEquals(Duration.ofMinutes(4), result.getRetryAfter());
        assertThrows(
                IllegalArgumentException.class,
                () -> AdvanceResult.waitingRemote("export=abc", Duration.ofSeconds(-1), "REMOTE_PENDING")
        );
        assertNull(AdvanceResult.succeeded().getRetryAfter());
    }

    @Test
    void operationHandlerAdvancesThroughOneSmallInterface() {
        OperationHandler<String> handler = new OperationHandler<String>() {
            @Override
            public OperationCode operationCode() {
                return OperationCode.DP04;
            }

            @Override
            public AdvanceResult advance(String context) {
                return AdvanceResult.queued(context);
            }
        };

        assertEquals(OperationCode.DP04, handler.operationCode());
        assertEquals("page=2", handler.advance("page=2").getCheckpoint());
    }
}
