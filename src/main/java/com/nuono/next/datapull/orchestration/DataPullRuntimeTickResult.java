package com.nuono.next.datapull.orchestration;

/** Technical counters returned by one short runtime tick. */
public final class DataPullRuntimeTickResult {

    private final int reconciledTasks;
    private final int claimedTasks;
    private final int inFlightTasks;

    public DataPullRuntimeTickResult(int reconciledTasks, int claimedTasks, int inFlightTasks) {
        this.reconciledTasks = requireNonNegative(reconciledTasks, "reconciledTasks");
        this.claimedTasks = requireNonNegative(claimedTasks, "claimedTasks");
        this.inFlightTasks = requireNonNegative(inFlightTasks, "inFlightTasks");
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    public int getReconciledTasks() {
        return reconciledTasks;
    }

    public int getClaimedTasks() {
        return claimedTasks;
    }

    public int getInFlightTasks() {
        return inFlightTasks;
    }
}
