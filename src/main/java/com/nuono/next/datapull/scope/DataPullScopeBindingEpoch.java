package com.nuono.next.datapull.scope;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.Objects;

/** Persisted temporal epoch; payload bytes and digest never change after insert. */
public class DataPullScopeBindingEpoch {
    private String bindingId;
    private OperationCode operationCode;
    private String scopeKey;
    private String payloadType;
    private String payloadSha256;
    private String payload;
    private LocalDateTime effectiveFromUtc;
    private LocalDateTime effectiveUntilUtc;
    private LocalDateTime sourceObservedAtUtc;
    private LocalDateTime createdAtUtc;
    private LocalDateTime updatedAtUtc;

    public static DataPullScopeBindingEpoch open(
            DataPullScopeBindingCandidate candidate,
            LocalDateTime observedAtUtc
    ) {
        DataPullScopeBindingCandidate source = Objects.requireNonNull(candidate, "candidate");
        DataPullScopeBindingEpoch result = new DataPullScopeBindingEpoch();
        result.bindingId = source.getBindingId();
        result.operationCode = source.getOperationCode();
        result.scopeKey = source.getScopeKey();
        result.payloadType = source.getPayloadType();
        result.payloadSha256 = source.getPayloadSha256();
        result.payload = source.getPayload();
        result.effectiveFromUtc = source.getEffectiveFromUtc();
        result.sourceObservedAtUtc = observedAtUtc;
        result.createdAtUtc = observedAtUtc;
        result.updatedAtUtc = observedAtUtc;
        result.validate();
        return result;
    }

    public void validate() {
        DataPullScopeBindingCandidate.requireDigest(bindingId, "bindingId");
        Objects.requireNonNull(operationCode, "operationCode");
        DataPullScopeBindingCandidate.requireIdentity(scopeKey, "scopeKey", 96);
        DataPullScopeBindingCandidate.requireIdentity(payloadType, "payloadType", 64);
        DataPullScopeBindingCandidate.requireDigest(payloadSha256, "payloadSha256");
        DataPullScopeBindingCandidate.requirePayload(payload);
        LocalDateTime start = DataPullScopeBindingCandidate.requireMillisecond(
                effectiveFromUtc, "effectiveFromUtc"
        );
        if (effectiveUntilUtc != null && !effectiveUntilUtc.isAfter(start)) {
            throw new IllegalStateException("scope binding epoch must have a positive window");
        }
        LocalDateTime observed = DataPullScopeBindingCandidate.requireMillisecond(
                sourceObservedAtUtc, "sourceObservedAtUtc"
        );
        if (observed.isBefore(start)) {
            throw new IllegalStateException("scope binding cannot predate provider observation");
        }
        if (!DataPullScopeBindingCandidate.sha256(payload).equals(payloadSha256)) {
            throw new IllegalStateException("scope binding payload digest drift");
        }
    }

    public boolean samePayload(DataPullScopeBindingCandidate candidate) {
        DataPullScopeBindingCandidate value = Objects.requireNonNull(candidate, "candidate");
        validate();
        return operationCode == value.getOperationCode()
                && scopeKey.equals(value.getScopeKey())
                && payloadType.equals(value.getPayloadType())
                && payloadSha256.equals(value.getPayloadSha256())
                && payload.equals(value.getPayload());
    }

    public String getBindingId() { return bindingId; }
    public void setBindingId(String value) { bindingId = value; }
    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode value) { operationCode = value; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public String getPayloadType() { return payloadType; }
    public void setPayloadType(String value) { payloadType = value; }
    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String value) { payloadSha256 = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { payload = value; }
    public LocalDateTime getEffectiveFromUtc() { return effectiveFromUtc; }
    public void setEffectiveFromUtc(LocalDateTime value) { effectiveFromUtc = value; }
    public LocalDateTime getEffectiveUntilUtc() { return effectiveUntilUtc; }
    public void setEffectiveUntilUtc(LocalDateTime value) { effectiveUntilUtc = value; }
    public LocalDateTime getSourceObservedAtUtc() { return sourceObservedAtUtc; }
    public void setSourceObservedAtUtc(LocalDateTime value) { sourceObservedAtUtc = value; }
    public LocalDateTime getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(LocalDateTime value) { createdAtUtc = value; }
    public LocalDateTime getUpdatedAtUtc() { return updatedAtUtc; }
    public void setUpdatedAtUtc(LocalDateTime value) { updatedAtUtc = value; }
}
