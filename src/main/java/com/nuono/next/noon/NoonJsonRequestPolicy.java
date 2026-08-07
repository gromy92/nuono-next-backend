package com.nuono.next.noon;

enum NoonJsonRequestPolicy {
    READ_WITH_RETRY(true, true),
    ONE_SHOT_FAIL_FAST(false, false),
    ONE_SHOT_AFTER_PACING(false, true);

    private final boolean retryTransientFailures;
    private final boolean waitForPacing;

    NoonJsonRequestPolicy(boolean retryTransientFailures, boolean waitForPacing) {
        this.retryTransientFailures = retryTransientFailures;
        this.waitForPacing = waitForPacing;
    }

    boolean retryTransientFailures() {
        return retryTransientFailures;
    }

    boolean waitForPacing() {
        return waitForPacing;
    }
}
