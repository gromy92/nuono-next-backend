package com.nuono.next.datapull.orchestration;

/** Keeps deadline, shutdown, and interrupt signals out of ordinary failure isolation. */
final class DataPullRuntimeCancellation {

    private DataPullRuntimeCancellation() { }

    static void requireActive() {
        DataPullAdvanceDeadline.requireRemaining();
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("DP_RUNTIME_INTERRUPTED");
        }
    }

    static void rethrowIfCancellation(RuntimeException failure) {
        rethrowIfCancellation(failure, null);
    }

    static void rethrowIfCancellation(
            RuntimeException failure,
            DataPullRuntimeStopSignal stopSignal
    ) {
        DataPullAdvanceDeadline deadline = DataPullAdvanceDeadline.current();
        if ((stopSignal != null && stopSignal.isStopping())
                || Thread.currentThread().isInterrupted()
                || (deadline != null && deadline.isExpired())) {
            throw failure;
        }
    }
}
