package com.nuono.next.datapull.persistence;

/** Exact CAS identity for requeueing one investigated terminal failure. */
public final class DataPullTaskRepairCommand {
    private final long taskId;
    private final long expectedVersion;
    private final long expectedFenceEpoch;
    private final String expectedFailureCode;

    public DataPullTaskRepairCommand(
            long taskId,
            long expectedVersion,
            long expectedFenceEpoch,
            String expectedFailureCode
    ) {
        if (taskId <= 0L || expectedVersion < 0L || expectedFenceEpoch <= 0L) {
            throw new IllegalArgumentException("repair fence identity is invalid");
        }
        this.taskId = taskId;
        this.expectedVersion = expectedVersion;
        this.expectedFenceEpoch = expectedFenceEpoch;
        this.expectedFailureCode = DataPullTaskContract.optionalSanitizedCode(
                DataPullTaskContract.requireIdentity(expectedFailureCode, "expectedFailureCode")
        );
    }

    public long getTaskId() { return taskId; }
    public long getExpectedVersion() { return expectedVersion; }
    public long getExpectedFenceEpoch() { return expectedFenceEpoch; }
    public String getExpectedFailureCode() { return expectedFailureCode; }
}
