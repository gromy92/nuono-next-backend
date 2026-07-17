package com.nuono.next.noonpull;

import java.util.Optional;

/**
 * Pull-side boundary for coalescing an authentication failure into the durable shared-email
 * recovery queue. Implementations own identity/project resolution and must atomically persist the
 * recovery/project/item state together with {@link NoonPullRepository#blockTaskForAuth} before
 * returning a recovery id. An empty result means that automatic recovery is not enabled for the
 * task and the caller should apply its legacy failure policy.
 */
public interface NoonPullAuthRecoveryQueue {
    Optional<Long> blockAndEnqueue(NoonPullTaskRecord task, String rawFailure);
}
