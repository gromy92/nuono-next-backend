package com.nuono.next.noon;

import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;

final class NoonAuthQueueSignal {
    private NoonAuthWaitQueue queue = request -> java.util.Optional.empty();

    void setQueue(NoonAuthWaitQueue queue) {
        if (queue != null) {
            this.queue = queue;
        }
    }

    void enqueueIfAuthenticationFailure(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            RuntimeException failure
    ) {
        if (NoonAuthenticationFailureClassifier.isAuthenticationFailure(failure)
                && !NoonAuthenticationFailureClassifier
                        .hasPermanentAuthenticationFailureEvidence(failure)) {
            enqueue(ownerUserId, projectCode, storeCode);
        }
    }

    void enqueue(Long ownerUserId, String projectCode, String storeCode) {
        try {
            queue.enqueue(NoonAuthWaitRequest.binding(ownerUserId, projectCode, storeCode));
        } catch (RuntimeException ignored) {
            // Preserve the provider failure; a durable task may attach its exact source separately.
        }
    }
}
