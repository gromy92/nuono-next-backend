package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import java.time.LocalDateTime;
import java.util.Objects;

final class NoonAuthManualHoldReleaser {
    private static final String RATE_LIMITED = "SEND_RATE_LIMITED";

    private NoonAuthManualHoldReleaser() {
    }

    static int release(
            NoonAuthRecoveryMapper mapper,
            NoonAuthIdentityRecoveryRecord active,
            String identityKey,
            String configuredFingerprint,
            LocalDateTime rateLimitCooldownCutoff,
            LocalDateTime nextAttemptAt,
            LocalDateTime now
    ) {
        if (identityKey == null
                || identityKey.trim().isEmpty()
                || configuredFingerprint == null
                || configuredFingerprint.trim().isEmpty()
                || nextAttemptAt == null
                || now == null) {
            return 0;
        }
        if (eligibleRateLimit(
                active,
                identityKey,
                configuredFingerprint,
                rateLimitCooldownCutoff
        )) {
            int released = mapper.releaseEligibleRateLimitedManualHold(
                    active.getId(),
                    active.getVersionNo(),
                    identityKey,
                    configuredFingerprint,
                    rateLimitCooldownCutoff,
                    now,
                    now
            );
            if (released == 1) {
                mapper.releaseRateLimitedProjectHolds(active.getId(), now);
            }
            return released;
        }
        if (active == null || active.getId() == null) {
            return 0;
        }
        int released = mapper.releaseChangedManualHolds(
                identityKey,
                configuredFingerprint,
                nextAttemptAt,
                now
        );
        if (released == 1) {
            mapper.releaseProjectManualHolds(active.getId(), configuredFingerprint, now);
            mapper.reopenFailedRecoveryItems(active.getId(), now);
        }
        return released;
    }

    private static boolean eligibleRateLimit(
            NoonAuthIdentityRecoveryRecord active,
            String identityKey,
            String configuredFingerprint,
            LocalDateTime cooldownCutoff
    ) {
        if (active == null
                || active.getId() == null
                || active.getVersionNo() == null
                || active.getStatus() != NoonAuthRecoveryStatus.MANUAL_HOLD
                || !RATE_LIMITED.equals(active.getFailureCode())
                || !Objects.equals(identityKey, active.getIdentityKey())
                || !Objects.equals(configuredFingerprint, active.getConfigFingerprint())
                || active.getSendAttemptCount() == null
                || active.getSendAttemptCount() != 1
                || active.getSecondSendAt() != null
                || cooldownCutoff == null) {
            return false;
        }
        LocalDateTime firstSendAt = active.getFirstSendAt();
        return firstSendAt != null && !firstSendAt.isAfter(cooldownCutoff);
    }
}
