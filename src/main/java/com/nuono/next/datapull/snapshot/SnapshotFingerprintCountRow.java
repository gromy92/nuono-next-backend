package com.nuono.next.datapull.snapshot;

/** One key in the persisted two-pass content multiset. */
public final class SnapshotFingerprintCountRow {
    private String contentFingerprint;
    private Long passOneCount;
    private Long passTwoCount;

    public SnapshotFingerprintCountRow() {
    }

    SnapshotFingerprintCountRow(String fingerprint, long passOne, long passTwo) {
        this.contentFingerprint = fingerprint;
        this.passOneCount = passOne;
        this.passTwoCount = passTwo;
    }

    public String getContentFingerprint() { return contentFingerprint; }
    public void setContentFingerprint(String value) { contentFingerprint = value; }
    public Long getPassOneCount() { return passOneCount; }
    public void setPassOneCount(Long value) { passOneCount = value; }
    public Long getPassTwoCount() { return passTwoCount; }
    public void setPassTwoCount(Long value) { passTwoCount = value; }
}
