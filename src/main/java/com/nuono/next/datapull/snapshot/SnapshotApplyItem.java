package com.nuono.next.datapull.snapshot;

import java.util.Objects;

/** One canonical staged item decoded inside a bounded preparation transaction. */
public final class SnapshotApplyItem<T> {
    private final int pageNo;
    private final int itemOrdinal;
    private final String stableIdentity;
    private final String contentFingerprint;
    private final boolean validatedIdentityCandidate;
    private final boolean absenceReconciliationSafe;
    private final T value;

    SnapshotApplyItem(SnapshotStageItemRow row, T value) {
        this.pageNo = Objects.requireNonNull(row.getPageNo(), "pageNo");
        this.itemOrdinal = Objects.requireNonNull(row.getItemOrdinal(), "itemOrdinal");
        this.stableIdentity = Objects.requireNonNull(row.getStableIdentity(), "stableIdentity");
        this.contentFingerprint = Objects.requireNonNull(
                row.getContentFingerprint(), "contentFingerprint"
        );
        this.validatedIdentityCandidate = Objects.requireNonNull(
                row.getValidatedIdentityCandidate(), "validatedIdentityCandidate"
        );
        this.absenceReconciliationSafe = Objects.requireNonNull(
                row.getAbsenceReconciliationSafe(), "absenceReconciliationSafe"
        );
        this.value = Objects.requireNonNull(value, "value");
    }

    public int getPageNo() { return pageNo; }
    public int getItemOrdinal() { return itemOrdinal; }
    public String getStableIdentity() { return stableIdentity; }
    public String getContentFingerprint() { return contentFingerprint; }
    public boolean isValidatedIdentityCandidate() { return validatedIdentityCandidate; }
    public boolean isAbsenceReconciliationSafe() { return absenceReconciliationSafe; }
    public T getValue() { return value; }
}
