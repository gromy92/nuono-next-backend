package com.nuono.next.noon;

import java.time.Instant;

/** Safe account-login state returned to an authenticated Nuono operator. */
public final class NoonAccountSessionView {
    private final NoonAccountSessionStatus status;
    private final String challengeId;
    private final Instant expiresAt;
    private final String message;

    NoonAccountSessionView(
            NoonAccountSessionStatus status,
            String challengeId,
            Instant expiresAt,
            String message
    ) {
        this.status = status;
        this.challengeId = challengeId;
        this.expiresAt = expiresAt;
        this.message = message;
    }

    public NoonAccountSessionStatus getStatus() {
        return status;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getMessage() {
        return message;
    }
}
