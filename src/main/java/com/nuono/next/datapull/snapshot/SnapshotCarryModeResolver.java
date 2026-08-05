package com.nuono.next.datapull.snapshot;

import com.nuono.next.datapull.runtime.OperationCode;

/** Chooses the narrowest preservation mode supported by the classified snapshot facts. */
final class SnapshotCarryModeResolver {
    SnapshotCarryMode resolve(
            CompleteSnapshot<?> snapshot,
            SnapshotApplyProgressRow progress
    ) {
        if (progress.getAbsenceUnsafeItemCount() > 0L
                || snapshot.getBusinessSkippedItemCount() > 0L) {
            return SnapshotCarryMode.FULL;
        }
        if (snapshot.getOperationCode() == OperationCode.DP04
                && progress.getEffectiveItemCount() < progress.getPreparedItemCount()) {
            return SnapshotCarryMode.TARGETED;
        }
        return SnapshotCarryMode.NONE;
    }
}
