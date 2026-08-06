package com.nuono.next.datapull.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotCollectionAuthorityTest {
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 8, 2, 3, 0);

    @Test
    void providerTokenIsHashedIntoAStablePersistableEnvelope() {
        SnapshotCollectionAuthority first = authority("opaque-provider-generation", 3L);
        SnapshotCollectionAuthority replay = authority("opaque-provider-generation", 3L);

        assertEquals(first, replay);
        assertEquals(64, first.getGenerationTokenSha256().length());
        assertNotEquals("opaque-provider-generation", first.getGenerationTokenSha256());
        assertEquals(3L, first.getDeclaredCollectionCount());
        assertEquals(first, SnapshotCollectionAuthority.fromPersistedDigest(
                first.getKind(),
                first.getGenerationTokenSha256(),
                first.getProviderAsOfUtc(),
                first.getDeclaredCollectionCount()
        ));
    }

    @Test
    void unstableTokenPrecisionAndExtentAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> authority(" token", 1L));
        assertThrows(IllegalArgumentException.class, () -> authority("token", -1L));
        assertThrows(IllegalArgumentException.class, () ->
                SnapshotCollectionAuthority.fromPersistedDigest(
                        SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                        "raw-token",
                        AS_OF,
                        1L
                ));
        assertThrows(IllegalArgumentException.class, () ->
                SnapshotCollectionAuthority.fromProviderToken(
                        SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                        "token",
                        AS_OF.withNano(1),
                        1L
                ));
    }

    @Test
    void pageRequiresExactRawAcceptedAndBusinessSkipAccounting() {
        SnapshotCollectionAuthority authority = authority("generation", 2L);
        assertThrows(IllegalArgumentException.class, () -> new SnapshotPage<>(
                1, null, true, 1, List.of("accepted"), authority, 2, 0
        ));
    }

    @Test
    void missingAuthorityNeverSilentlySelectsTwoPassMode() {
        SnapshotPage<String> legacy = new SnapshotPage<>(
                1, null, true, 1, List.of("row")
        );
        SnapshotPage<String> twoPass = SnapshotPage.twoPassRequired(
                1, null, true, 1, List.of("row"), 1, 0
        );

        assertEquals(SnapshotPage.AuthorityMode.PROVIDER_AUTHORITY, legacy.getAuthorityMode());
        assertEquals(SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED, twoPass.getAuthorityMode());
    }

    @Test
    void twoPassSkippedCountCannotStandInForSkippedRowFingerprints() {
        assertThrows(IllegalArgumentException.class, () -> SnapshotPage.twoPassRequired(
                1, null, true, 1, List.of("accepted"), 2, 1
        ));
    }

    @Test
    void completeResponseAuthorityUsesExactBytesAndCannotUseATokenFactory() {
        SnapshotCollectionAuthority first = SnapshotCollectionAuthority.fromCompleteResponse(
                new byte[]{1, 2, 3}, 0L
        );
        SnapshotCollectionAuthority changed = SnapshotCollectionAuthority.fromCompleteResponse(
                new byte[]{1, 2, 4}, 0L
        );

        assertEquals(SnapshotCollectionAuthority.Kind.COMPLETE_RESPONSE, first.getKind());
        assertNotEquals(first.getGenerationTokenSha256(), changed.getGenerationTokenSha256());
        assertThrows(IllegalArgumentException.class, () ->
                SnapshotCollectionAuthority.fromProviderToken(
                        SnapshotCollectionAuthority.Kind.TWO_PASS_OBSERVATION,
                        "not-observation-authority", null, 0L
                ));
    }

    private SnapshotCollectionAuthority authority(String token, long count) {
        return SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                token,
                AS_OF,
                count
        );
    }
}
