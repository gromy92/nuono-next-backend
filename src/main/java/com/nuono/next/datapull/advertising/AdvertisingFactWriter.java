package com.nuono.next.datapull.advertising;

/** DP-owned atomic fact transaction. It must fence against the current RUNNING task epoch. */
public interface AdvertisingFactWriter {
    enum ApplyResult {
        MORE_WORK,
        APPLIED,
        ALREADY_APPLIED,
        STALE_FENCE,
        CONTRACT_ERROR
    }

    enum ResetResult {
        MORE_WORK,
        CLEARED,
        STALE_FENCE
    }

    ApplyResult applyComplete(AdvertisingApplyCommand command);

    ResetResult reset(long taskId, long fenceEpoch, String leaseOwner);
}
