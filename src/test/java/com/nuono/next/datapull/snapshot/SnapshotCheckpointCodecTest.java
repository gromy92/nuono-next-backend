package com.nuono.next.datapull.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SnapshotCheckpointCodecTest {
    private final SnapshotCheckpointCodec codec = new SnapshotCheckpointCodec();

    @Test
    void absentCheckpointStartsAtPageOne() {
        SnapshotCheckpoint checkpoint = codec.decode(null);

        assertEquals(SnapshotCheckpoint.Phase.FETCH, checkpoint.getPhase());
        assertEquals(1, checkpoint.getNextPage());
        assertFalse(checkpoint.getKnownLastPage().isPresent());
        assertEquals(0, checkpoint.getConsecutiveRetryAttempt());
    }

    @Test
    void roundTripsFetchVerifyCompareApplyAndResetWithoutLosingState() {
        SnapshotCheckpoint fetch = codec.decode(codec.encode(SnapshotCheckpoint.fetch(7, 12)));
        SnapshotCheckpoint verify = codec.decode(codec.encode(SnapshotCheckpoint.verify(3, 12)));
        SnapshotCheckpoint compare = codec.decode(codec.encode(SnapshotCheckpoint.compare(12)));
        SnapshotCheckpoint apply = codec.decode(codec.encode(SnapshotCheckpoint.apply(12)));
        SnapshotCheckpoint reset = codec.decode(codec.encode(SnapshotCheckpoint.resetting()));
        SnapshotCheckpoint retryingApply = codec.decode(codec.encode(
                SnapshotCheckpoint.apply(12).nextRetryAttempt()
        ));

        assertEquals(SnapshotCheckpoint.Phase.FETCH, fetch.getPhase());
        assertEquals(7, fetch.getNextPage());
        assertEquals(12, fetch.getKnownLastPage().orElseThrow());
        assertEquals(SnapshotCheckpoint.Phase.VERIFY, verify.getPhase());
        assertEquals(3, verify.getNextPage());
        assertEquals(SnapshotCheckpoint.Phase.COMPARE, compare.getPhase());
        assertEquals(SnapshotCheckpoint.Phase.APPLY, apply.getPhase());
        assertEquals(13, apply.getNextPage());
        assertEquals(12, apply.getKnownLastPage().orElseThrow());
        assertEquals(SnapshotCheckpoint.Phase.APPLY, retryingApply.getPhase());
        assertEquals(1, retryingApply.getConsecutiveRetryAttempt());
        assertEquals(SnapshotCheckpoint.Phase.RESET, reset.getPhase());
        assertEquals(1, reset.getNextPage());
        assertFalse(reset.getKnownLastPage().isPresent());
    }

    @Test
    void decodesLegacyV1ButRejectsUnknownVersionAndInvalidNewPhases() {
        assertEquals(SnapshotCheckpoint.Phase.FETCH, codec.decode(
                "v1|FETCH|1|-|0"
        ).getPhase());
        assertEquals(SnapshotCheckpoint.Phase.RESET, codec.decode(
                "v1|RESET|1|-|0"
        ).getPhase());
        assertThrows(IllegalArgumentException.class, () -> codec.decode("v3|FETCH|1|-|0"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("v1|APPLY|12|12|0"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("v1|FETCH|0|-|0"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("v1|RESET|2|-|0"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("v1|RESET|1|-|1"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("v1|VERIFY|1|2|0"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("v2|COMPARE|3|2|1"));
    }
}
