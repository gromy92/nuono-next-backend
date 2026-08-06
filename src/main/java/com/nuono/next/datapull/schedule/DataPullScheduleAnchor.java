package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable technical lower bound for one operation/scope schedule ledger. */
public final class DataPullScheduleAnchor {

    public enum Kind {
        CUTOVER_RECONCILED,
        POST_CUTOVER_SCOPE
    }

    private OperationCode operationCode;
    private String scopeKey;
    private String cutoverKey;
    private Kind anchorKind;
    private LocalDateTime reconcileAfterUtc;
    private LocalDateTime createdAtUtc;
    private DataPullScopeAdmission.Kind admissionKind;
    private LocalDateTime firstEligibleAtUtc;
    private String sourceBindingSha256;
    private String anchorEvidenceSha256;

    public DataPullScheduleAnchor() {
        // MyBatis bean constructor.
    }

    public static DataPullScheduleAnchor cutover(
            OperationCode operationCode,
            DataPullScopeAdmission admission,
            LocalDateTime reconcileAfterUtc,
            LocalDateTime createdAtUtc,
            String anchorEvidenceSha256
    ) {
        return create(
                operationCode,
                admission,
                Kind.CUTOVER_RECONCILED,
                reconcileAfterUtc,
                createdAtUtc,
                anchorEvidenceSha256
        );
    }

    public static DataPullScheduleAnchor postCutoverScope(
            OperationCode operationCode,
            DataPullScopeAdmission admission,
            LocalDateTime reconcileAfterUtc,
            LocalDateTime createdAtUtc
    ) {
        return create(
                operationCode,
                admission,
                Kind.POST_CUTOVER_SCOPE,
                reconcileAfterUtc,
                createdAtUtc,
                DataPullScheduleAnchorEvidence.postCutoverSha256(
                        operationCode,
                        admission,
                        reconcileAfterUtc
                )
        );
    }

    private static DataPullScheduleAnchor create(
            OperationCode operationCode,
            DataPullScopeAdmission admission,
            Kind kind,
            LocalDateTime reconcileAfterUtc,
            LocalDateTime createdAtUtc,
            String anchorEvidenceSha256
    ) {
        DataPullScopeAdmission admitted = Objects.requireNonNull(admission, "admission");
        admitted.validate();
        DataPullScheduleAnchor anchor = new DataPullScheduleAnchor();
        anchor.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        anchor.scopeKey = admitted.getScopeKey();
        anchor.cutoverKey = admitted.getCutoverKey();
        anchor.anchorKind = Objects.requireNonNull(kind, "anchorKind");
        anchor.reconcileAfterUtc = Objects.requireNonNull(reconcileAfterUtc, "reconcileAfterUtc");
        anchor.createdAtUtc = Objects.requireNonNull(createdAtUtc, "createdAtUtc");
        anchor.admissionKind = admitted.getAdmissionKind();
        anchor.firstEligibleAtUtc = admitted.getFirstEligibleAtUtc();
        anchor.sourceBindingSha256 = admitted.getSourceBindingSha256();
        anchor.anchorEvidenceSha256 = anchorEvidenceSha256;
        anchor.validate();
        return anchor;
    }

    public void validate() {
        Objects.requireNonNull(operationCode, "operationCode");
        requireIdentity(scopeKey, "scopeKey", 96);
        requireIdentity(cutoverKey, "cutoverKey", 96);
        Objects.requireNonNull(anchorKind, "anchorKind");
        DataPullScopeAdmission.requireMillisecond(reconcileAfterUtc, "reconcileAfterUtc");
        DataPullScopeAdmission.requireMillisecond(createdAtUtc, "createdAtUtc");
        Objects.requireNonNull(admissionKind, "admissionKind");
        DataPullScopeAdmission.requireDigest(sourceBindingSha256, "sourceBindingSha256");
        DataPullScopeAdmission.requireDigest(anchorEvidenceSha256, "anchorEvidenceSha256");
        if (anchorKind == Kind.CUTOVER_RECONCILED) {
            if (admissionKind != DataPullScopeAdmission.Kind.CUTOVER_EXISTING
                    || firstEligibleAtUtc != null) {
                throw new IllegalStateException("cutover anchor admission evidence is invalid");
            }
        } else {
            if (admissionKind != DataPullScopeAdmission.Kind.POST_CUTOVER) {
                throw new IllegalStateException("post-cutover anchor requires post admission");
            }
            DataPullScopeAdmission.requireMillisecond(firstEligibleAtUtc, "firstEligibleAtUtc");
            if (createdAtUtc.isBefore(firstEligibleAtUtc)) {
                throw new IllegalStateException("post-cutover anchor predates eligibility");
            }
            String expected = DataPullScheduleAnchorEvidence.postCutoverSha256(
                    operationCode, scopeKey, admissionKind, firstEligibleAtUtc,
                    sourceBindingSha256, cutoverKey, reconcileAfterUtc
            );
            if (!expected.equals(anchorEvidenceSha256)) {
                throw new IllegalStateException("post-cutover anchor evidence digest mismatch");
            }
        }
    }

    static String requireIdentity(String value, String field, int maximumLength) {
        if (value == null || value.isEmpty() || !value.equals(value.trim())
                || value.length() > maximumLength || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " must be a stable persisted identity");
        }
        return value;
    }

    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode value) { operationCode = value; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public String getCutoverKey() { return cutoverKey; }
    public void setCutoverKey(String value) { cutoverKey = value; }
    public Kind getAnchorKind() { return anchorKind; }
    public void setAnchorKind(Kind value) { anchorKind = value; }
    public LocalDateTime getReconcileAfterUtc() { return reconcileAfterUtc; }
    public void setReconcileAfterUtc(LocalDateTime value) { reconcileAfterUtc = value; }
    public LocalDateTime getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(LocalDateTime value) { createdAtUtc = value; }
    public DataPullScopeAdmission.Kind getAdmissionKind() { return admissionKind; }
    public void setAdmissionKind(DataPullScopeAdmission.Kind value) { admissionKind = value; }
    public LocalDateTime getFirstEligibleAtUtc() { return firstEligibleAtUtc; }
    public void setFirstEligibleAtUtc(LocalDateTime value) { firstEligibleAtUtc = value; }
    public String getSourceBindingSha256() { return sourceBindingSha256; }
    public void setSourceBindingSha256(String value) { sourceBindingSha256 = value; }
    public String getAnchorEvidenceSha256() { return anchorEvidenceSha256; }
    public void setAnchorEvidenceSha256(String value) { anchorEvidenceSha256 = value; }
}
