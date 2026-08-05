package com.nuono.next.noonauth;

import java.time.LocalDateTime;

/** Business-owned state transition for one durable task waiting on Project authorization. */
public interface NoonAuthWaitingTaskHandler {
    boolean supports(String sourceDomain);

    NoonAuthWaitingTaskOutcome resume(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            LocalDateTime now
    );

    NoonAuthWaitingTaskOutcome fail(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            String failureCode,
            String diagnostic,
            LocalDateTime now
    );

    /**
     * Stops autonomous retries while retaining the source task for an explicit manual recovery.
     * Legacy BLOCKED_AUTH tasks are already untimed, so handlers only override this when their
     * source runtime also has a timed retry path.
     */
    default NoonAuthWaitingTaskOutcome hold(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            String failureCode,
            String diagnostic,
            LocalDateTime now
    ) {
        return NoonAuthWaitingTaskOutcome.MANUAL_REVIEW;
    }
}
