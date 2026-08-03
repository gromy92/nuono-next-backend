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
}
