package com.nuono.next.datapull.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DataPullTaskColumnLengthTest {

    @Test
    void enqueueRejectsStoreIdentityThatCannotFitTheRuntimeLedger() {
        DataPullTask task = DataPullTask.queued(
                1L,
                OperationCode.DP04,
                "provider",
                307L,
                8001L,
                "account",
                null,
                "P".repeat(101),
                "store",
                "SA",
                "scope",
                LocalDateTime.of(2026, 8, 2, 3, 0),
                "window",
                "step",
                LocalDateTime.of(2026, 8, 2, 2, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> DataPullTaskContract.requireEnqueueable(task)
        );
    }

    @Test
    void enqueueRejectsEveryOversizedRuntimeIdentity() {
        assertDoesNotThrow(() -> DataPullTaskContract.requireEnqueueable(task(
                "P".repeat(64), "A".repeat(160), "E".repeat(160),
                "S".repeat(96), "W".repeat(160), "T".repeat(80)
        )));
        assertRejected(task("P".repeat(65), "account", null, "scope", "window", "step"));
        assertRejected(task("provider", "A".repeat(161), null, "scope", "window", "step"));
        assertRejected(task("provider", "account", "E".repeat(161), "scope", "window", "step"));
        assertRejected(task("provider", "account", null, "S".repeat(97), "window", "step"));
        assertRejected(task("provider", "account", null, "scope", "W".repeat(161), "step"));
        assertRejected(task("provider", "account", null, "scope", "window", "T".repeat(81)));
    }

    private void assertRejected(DataPullTask task) {
        assertThrows(
                IllegalArgumentException.class,
                () -> DataPullTaskContract.requireEnqueueable(task)
        );
    }

    private DataPullTask task(
            String provider,
            String account,
            String egress,
            String scope,
            String window,
            String step
    ) {
        return DataPullTask.queued(
                1L,
                OperationCode.DP04,
                provider,
                307L,
                8001L,
                account,
                egress,
                "project",
                "store",
                "SA",
                scope,
                LocalDateTime.of(2026, 8, 2, 3, 0),
                window,
                step,
                LocalDateTime.of(2026, 8, 2, 2, 0)
        );
    }
}
