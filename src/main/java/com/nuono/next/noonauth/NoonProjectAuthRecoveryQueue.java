package com.nuono.next.noonauth;

import java.util.Optional;

/**
 * Submits a Project-only authentication recovery request to the shared-email queue.
 *
 * <p>This is used by backend binding flows that do not yet have a pull task to block. The
 * implementation must never perform OTP work in the caller's request thread.</p>
 */
public interface NoonProjectAuthRecoveryQueue {

    Optional<Long> enqueueProject(Long ownerUserId, String projectCode, String storeCode);
}
