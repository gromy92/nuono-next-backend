package com.nuono.next.procurement.aliorder.datapull;

/** One exact raw-row fingerprint multiplicity for the two fixed-window list passes. */
public final class Ali1688Dp10FingerprintCountRow {
    private String fingerprint;
    private Long passOneCount;
    private Long passTwoCount;

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String value) { fingerprint = value; }
    public Long getPassOneCount() { return passOneCount; }
    public void setPassOneCount(Long value) { passOneCount = value; }
    public Long getPassTwoCount() { return passTwoCount; }
    public void setPassTwoCount(Long value) { passTwoCount = value; }
}
