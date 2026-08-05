package com.nuono.next.datapull.snapshot;

/** Structural validation for a locked materialization cursor. */
final class SnapshotApplyProgressValidator {
    boolean validCarryState(SnapshotApplyProgressRow progress) {
        if ("PREPARING".equals(progress.getState())) {
            return progress.getCarryMode() == SnapshotCarryMode.NONE
                    && progress.getCarrySourceTaskId() == null
                    && progress.getCarrySourceHeadVersion() == null
                    && progress.getCarryCursorIdentity() == null
                    && progress.getEffectiveItemCount() <= progress.getPreparedItemCount();
        }
        String cursor = progress.getCarryCursorIdentity();
        return "CARRYING".equals(progress.getState())
                && progress.getCarryMode() != null
                && progress.getCarryMode() != SnapshotCarryMode.NONE
                && progress.getCarrySourceTaskId() != null
                && progress.getCarrySourceTaskId() > 0L
                && progress.getCarrySourceTaskId() < progress.getTaskId()
                && progress.getCarrySourceHeadVersion() != null
                && progress.getCarrySourceHeadVersion() >= 0L
                && (cursor == null || stable(cursor));
    }

    private boolean stable(String value) {
        return !value.isEmpty() && value.equals(value.trim()) && value.indexOf('\0') < 0;
    }
}
