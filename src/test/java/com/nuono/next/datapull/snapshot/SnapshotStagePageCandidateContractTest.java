package com.nuono.next.datapull.snapshot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotStagePageCandidateContractTest {

    private static final String VALID_FINGERPRINT = "a".repeat(64);

    @Test
    void identityMatchesItsPersistenceColumn() {
        assertDoesNotThrow(() -> candidate("i".repeat(240), VALID_FINGERPRINT, "{}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate("i".repeat(241), VALID_FINGERPRINT, "{}")
        );
    }

    @Test
    void fingerprintIsExactlyLowercaseSha256Shape() {
        assertDoesNotThrow(() -> candidate("item", VALID_FINGERPRINT, "{}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate("item", "a".repeat(63), "{}")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate("item", "A".repeat(64), "{}")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate("item", "g".repeat(64), "{}")
        );
    }

    @Test
    void payloadLimitIsMeasuredInUtf8Bytes() {
        assertDoesNotThrow(() -> candidate(
                "item", VALID_FINGERPRINT, "é".repeat(8_355_840)
        ));
        assertThrows(IllegalArgumentException.class, () -> candidate(
                "item", VALID_FINGERPRINT, "a" + "é".repeat(8_355_840)
        ));
    }

    private SnapshotStagePageCandidate<String> candidate(
            String identity,
            String fingerprint,
            String payload
    ) {
        SnapshotItemDescriptor<String> descriptor = new SnapshotItemDescriptor<>() {
            @Override
            public String stableIdentity(String item) {
                return identity;
            }

            @Override
            public String stableContentFingerprint(String item) {
                return fingerprint;
            }
        };
        SnapshotPayloadCodec<String> codec = new SnapshotPayloadCodec<>() {
            @Override
            public String encode(String item) {
                return payload;
            }

            @Override
            public String decode(String encoded) {
                return encoded;
            }
        };
        return SnapshotStagePageCandidate.from(
                new SnapshotPage<>(1, null, true, 1, List.of("item")),
                descriptor,
                codec
        );
    }
}
