package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;

/** Current bounded source-scan epoch header. */
public class ScheduleSourceEpochRow {
    private OperationCode operationCode;
    private Long epochNo;
    private String cutoverKey;
    private String epochState;
    private LocalDateTime reconcileUntilUtc;
    private String passOneCursor;
    private Long passOneScopeCount;
    private String passOneOrderedSha256;
    private String passTwoCursor;
    private Long passTwoScopeCount;
    private String passTwoOrderedSha256;
    private String admissionCursorScopeKey;
    private String bindingCursorScopeKey;
    private String missingBindingCursorScopeKey;
    private String scheduleCursorScopeKey;
    private String bindingCloseState;
    private Long version;
    private LocalDateTime sealedAtUtc;
    private LocalDateTime terminalAtUtc;

    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode value) { operationCode = value; }
    public Long getEpochNo() { return epochNo; }
    public void setEpochNo(Long value) { epochNo = value; }
    public String getCutoverKey() { return cutoverKey; }
    public void setCutoverKey(String value) { cutoverKey = value; }
    public String getEpochState() { return epochState; }
    public void setEpochState(String value) { epochState = value; }
    public LocalDateTime getReconcileUntilUtc() { return reconcileUntilUtc; }
    public void setReconcileUntilUtc(LocalDateTime value) { reconcileUntilUtc = value; }
    public String getPassOneCursor() { return passOneCursor; }
    public void setPassOneCursor(String value) { passOneCursor = value; }
    public Long getPassOneScopeCount() { return passOneScopeCount; }
    public void setPassOneScopeCount(Long value) { passOneScopeCount = value; }
    public String getPassOneOrderedSha256() { return passOneOrderedSha256; }
    public void setPassOneOrderedSha256(String value) { passOneOrderedSha256 = value; }
    public String getPassTwoCursor() { return passTwoCursor; }
    public void setPassTwoCursor(String value) { passTwoCursor = value; }
    public Long getPassTwoScopeCount() { return passTwoScopeCount; }
    public void setPassTwoScopeCount(Long value) { passTwoScopeCount = value; }
    public String getPassTwoOrderedSha256() { return passTwoOrderedSha256; }
    public void setPassTwoOrderedSha256(String value) { passTwoOrderedSha256 = value; }
    public String getAdmissionCursorScopeKey() { return admissionCursorScopeKey; }
    public void setAdmissionCursorScopeKey(String value) { admissionCursorScopeKey = value; }
    public String getBindingCursorScopeKey() { return bindingCursorScopeKey; }
    public void setBindingCursorScopeKey(String value) { bindingCursorScopeKey = value; }
    public String getMissingBindingCursorScopeKey() { return missingBindingCursorScopeKey; }
    public void setMissingBindingCursorScopeKey(String value) { missingBindingCursorScopeKey = value; }
    public String getScheduleCursorScopeKey() { return scheduleCursorScopeKey; }
    public void setScheduleCursorScopeKey(String value) { scheduleCursorScopeKey = value; }
    public String getBindingCloseState() { return bindingCloseState; }
    public void setBindingCloseState(String value) { bindingCloseState = value; }
    public Long getVersion() { return version; }
    public void setVersion(Long value) { version = value; }
    public LocalDateTime getSealedAtUtc() { return sealedAtUtc; }
    public void setSealedAtUtc(LocalDateTime value) { sealedAtUtc = value; }
    public LocalDateTime getTerminalAtUtc() { return terminalAtUtc; }
    public void setTerminalAtUtc(LocalDateTime value) { terminalAtUtc = value; }
}
