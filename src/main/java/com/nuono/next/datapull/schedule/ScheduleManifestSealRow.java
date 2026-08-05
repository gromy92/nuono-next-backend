package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;

/** Persistent resumable verification state bound to one authoritative cutover manifest. */
public class ScheduleManifestSealRow {
    private OperationCode operationCode;
    private String cutoverKey;
    private Integer expectedScopeCount;
    private String expectedManifestSha256;
    private String sealState;
    private String nextScopeKey;
    private Integer scannedScopeCount;
    private String resumableSha256State;
    private String verifiedManifestSha256;
    private Long version;
    private LocalDateTime sealedAtUtc;

    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode value) { operationCode = value; }
    public String getCutoverKey() { return cutoverKey; }
    public void setCutoverKey(String value) { cutoverKey = value; }
    public Integer getExpectedScopeCount() { return expectedScopeCount; }
    public void setExpectedScopeCount(Integer value) { expectedScopeCount = value; }
    public String getExpectedManifestSha256() { return expectedManifestSha256; }
    public void setExpectedManifestSha256(String value) { expectedManifestSha256 = value; }
    public String getSealState() { return sealState; }
    public void setSealState(String value) { sealState = value; }
    public String getNextScopeKey() { return nextScopeKey; }
    public void setNextScopeKey(String value) { nextScopeKey = value; }
    public Integer getScannedScopeCount() { return scannedScopeCount; }
    public void setScannedScopeCount(Integer value) { scannedScopeCount = value; }
    public String getResumableSha256State() { return resumableSha256State; }
    public void setResumableSha256State(String value) { resumableSha256State = value; }
    public String getVerifiedManifestSha256() { return verifiedManifestSha256; }
    public void setVerifiedManifestSha256(String value) { verifiedManifestSha256 = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public LocalDateTime getSealedAtUtc() { return sealedAtUtc; }
    public void setSealedAtUtc(LocalDateTime value) { sealedAtUtc = value; }
}
