package com.nuono.next.noonauth;

import java.util.Objects;

public final class NoonAuthTransientBackoffWriteFence {
    private final Long recoveryId;
    private final NoonAuthRecoveryStatus expectedStatus;
    private final long expectedVersion;
    private final String expectedLeaseToken;

    public NoonAuthTransientBackoffWriteFence(
            Long recoveryId,
            NoonAuthRecoveryStatus expectedStatus,
            long expectedVersion,
            String expectedLeaseToken
    ) {
        this.recoveryId = Objects.requireNonNull(recoveryId, "recoveryId must not be null");
        this.expectedStatus = Objects.requireNonNull(
                expectedStatus,
                "expectedStatus must not be null"
        );
        this.expectedVersion = expectedVersion;
        this.expectedLeaseToken = Objects.requireNonNull(
                expectedLeaseToken,
                "expectedLeaseToken must not be null"
        );
    }

    public Long getRecoveryId() {
        return recoveryId;
    }

    public NoonAuthRecoveryStatus getExpectedStatus() {
        return expectedStatus;
    }

    public long getExpectedVersion() {
        return expectedVersion;
    }

    public String getExpectedLeaseToken() {
        return expectedLeaseToken;
    }
}
