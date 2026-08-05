package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopeBindingDigest;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Objects;

/** Persisted immutable pass-one source item plus bounded apply cursors. */
public class ScheduleSourceStageRow {
    private OperationCode operationCode;
    private Long epochNo;
    private String sourceCursor;
    private String sourceCursorSha256;
    private String scopeKey;
    private String scopeNamespace;
    private Long ownerUserId;
    private Long logicalStoreId;
    private String accountKey;
    private String egressKey;
    private String projectCode;
    private String storeCode;
    private String siteCode;
    private String immutablePayloadSha256;
    private String bindingPayloadType;
    private String bindingPayloadSha256;
    private String bindingPayload;
    private LocalDateTime bindingEffectiveFromUtc;
    private String admissionAnchorState;
    private String bindingState;
    private LocalDateTime reconcileAfterUtc;
    private LocalDateTime scheduleAfterUtc;
    private String scheduleState;

    public static ScheduleSourceStageRow from(
            OperationCode operation,
            long epochNo,
            ScheduleSourceScope source
    ) {
        ScheduleSourceScope item = Objects.requireNonNull(source, "source");
        DataPullScope scope = item.getScope();
        ScheduleSourceStageRow row = new ScheduleSourceStageRow();
        row.operationCode = Objects.requireNonNull(operation, "operation");
        row.epochNo = epochNo;
        row.sourceCursor = item.getSourceCursor();
        row.sourceCursorSha256 = sha256(row.sourceCursor);
        row.scopeKey = scope.getStableScopeKey();
        row.scopeNamespace = scope.getNamespace();
        row.ownerUserId = scope.getOwnerUserId();
        row.logicalStoreId = scope.getLogicalStoreId();
        row.accountKey = scope.getAccountKey();
        row.egressKey = scope.getEgressKey();
        row.projectCode = scope.getProjectCode();
        row.storeCode = scope.getStoreCode();
        row.siteCode = scope.getSiteCode();
        row.immutablePayloadSha256 = item.getImmutablePayloadSha256();
        DataPullScopeBindingCandidate binding = item.getBinding();
        if (binding == null) {
            row.bindingState = "NOT_REQUIRED";
        } else {
            row.bindingPayloadType = binding.getPayloadType();
            row.bindingPayloadSha256 = binding.getPayloadSha256();
            row.bindingPayload = binding.getPayload();
            row.bindingEffectiveFromUtc = binding.getEffectiveFromUtc();
            row.bindingState = "PENDING";
        }
        row.admissionAnchorState = "PENDING";
        row.scheduleState = "PENDING";
        return row;
    }

    public DataPullScope toScope() {
        return new DataPullScope(
                scopeNamespace, ownerUserId, logicalStoreId, accountKey, egressKey,
                projectCode, storeCode, siteCode, scopeKey
        );
    }

    public DataPullScopeBindingCandidate toBinding() {
        if (bindingPayloadType == null) return null;
        DataPullScopeBindingCandidate binding = new DataPullScopeBindingCandidate(
                operationCode, scopeKey, bindingPayloadType,
                bindingPayload, bindingEffectiveFromUtc
        );
        if (!binding.getPayloadSha256().equals(bindingPayloadSha256)) {
            throw new IllegalStateException("staged binding payload digest drift");
        }
        return binding;
    }

    public String getSourceBindingSha256() {
        return DataPullScopeBindingDigest.sha256(toScope());
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode v) { operationCode=v; }
    public Long getEpochNo() { return epochNo; }
    public void setEpochNo(Long v) { epochNo=v; }
    public String getSourceCursor() { return sourceCursor; }
    public void setSourceCursor(String v) { sourceCursor=v; }
    public String getSourceCursorSha256() { return sourceCursorSha256; }
    public void setSourceCursorSha256(String v) { sourceCursorSha256=v; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String v) { scopeKey=v; }
    public String getScopeNamespace() { return scopeNamespace; }
    public void setScopeNamespace(String v) { scopeNamespace=v; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long v) { ownerUserId=v; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public void setLogicalStoreId(Long v) { logicalStoreId=v; }
    public String getAccountKey() { return accountKey; }
    public void setAccountKey(String v) { accountKey=v; }
    public String getEgressKey() { return egressKey; }
    public void setEgressKey(String v) { egressKey=v; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String v) { projectCode=v; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String v) { storeCode=v; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String v) { siteCode=v; }
    public String getImmutablePayloadSha256() { return immutablePayloadSha256; }
    public void setImmutablePayloadSha256(String v) { immutablePayloadSha256=v; }
    public String getBindingPayloadType() { return bindingPayloadType; }
    public void setBindingPayloadType(String v) { bindingPayloadType=v; }
    public String getBindingPayloadSha256() { return bindingPayloadSha256; }
    public void setBindingPayloadSha256(String v) { bindingPayloadSha256=v; }
    public String getBindingPayload() { return bindingPayload; }
    public void setBindingPayload(String v) { bindingPayload=v; }
    public LocalDateTime getBindingEffectiveFromUtc() { return bindingEffectiveFromUtc; }
    public void setBindingEffectiveFromUtc(LocalDateTime v) { bindingEffectiveFromUtc=v; }
    public String getAdmissionAnchorState() { return admissionAnchorState; }
    public void setAdmissionAnchorState(String v) { admissionAnchorState=v; }
    public String getBindingState() { return bindingState; }
    public void setBindingState(String v) { bindingState=v; }
    public LocalDateTime getReconcileAfterUtc() { return reconcileAfterUtc; }
    public void setReconcileAfterUtc(LocalDateTime v) { reconcileAfterUtc=v; }
    public LocalDateTime getScheduleAfterUtc() { return scheduleAfterUtc; }
    public void setScheduleAfterUtc(LocalDateTime v) { scheduleAfterUtc=v; }
    public String getScheduleState() { return scheduleState; }
    public void setScheduleState(String v) { scheduleState=v; }
}
