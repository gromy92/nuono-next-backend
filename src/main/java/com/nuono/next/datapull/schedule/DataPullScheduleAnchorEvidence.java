package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** Deterministic technical-frontier proof for post-cutover anchors. */
public final class DataPullScheduleAnchorEvidence {

    private static final String POST_CUTOVER_VERSION =
            "DP_POST_CUTOVER_ANCHOR_EVIDENCE_V1";
    private static final String CUTOVER_VERSION = "DP_CUTOVER_ANCHOR_EVIDENCE_V1";

    private DataPullScheduleAnchorEvidence() {
    }

    public static String postCutoverSha256(
            OperationCode operationCode,
            DataPullScopeAdmission admission,
            LocalDateTime reconcileAfterUtc
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        DataPullScopeAdmission source = Objects.requireNonNull(admission, "admission");
        source.validate();
        if (source.getAdmissionKind() != DataPullScopeAdmission.Kind.POST_CUTOVER) {
            throw new IllegalArgumentException("post-cutover evidence requires post admission");
        }
        return postCutoverSha256(
                operation,
                source.getScopeKey(),
                source.getAdmissionKind(),
                source.getFirstEligibleAtUtc(),
                source.getSourceBindingSha256(),
                source.getCutoverKey(),
                reconcileAfterUtc
        );
    }

    /** Binds a conservative release boundary to one exact cutover scope identity. */
    public static String cutoverSha256(
            OperationCode operationCode,
            DataPullScopeAdmission admission,
            LocalDateTime reconcileAfterUtc,
            String boundaryKind,
            String boundaryEvidenceSha256
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        DataPullScopeAdmission source = Objects.requireNonNull(admission, "admission");
        source.validate();
        if (source.getAdmissionKind() != DataPullScopeAdmission.Kind.CUTOVER_EXISTING) {
            throw new IllegalArgumentException("cutover evidence requires existing admission");
        }
        LocalDateTime frontier = DataPullScopeAdmission.requireMillisecond(
                reconcileAfterUtc, "reconcileAfterUtc"
        );
        if (boundaryKind == null || !boundaryKind.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("cutover boundary kind is invalid");
        }
        String proof = DataPullScopeAdmission.requireDigest(
                boundaryEvidenceSha256, "boundaryEvidenceSha256"
        );
        MessageDigest digest = newDigest();
        update(digest, CUTOVER_VERSION);
        update(digest, operation.name());
        update(digest, source.getScopeKey());
        update(digest, DataPullScheduleAnchor.Kind.CUTOVER_RECONCILED.name());
        update(digest, source.getAdmissionKind().name());
        update(digest, source.getSourceBindingSha256());
        update(digest, source.getCutoverKey());
        update(digest, epochMillis(frontier));
        update(digest, boundaryKind);
        update(digest, proof);
        return hex(digest.digest());
    }

    static String postCutoverSha256(
            OperationCode operationCode,
            String scopeKey,
            DataPullScopeAdmission.Kind admissionKind,
            LocalDateTime firstEligibleAtUtc,
            String sourceBindingSha256,
            String cutoverKey,
            LocalDateTime reconcileAfterUtc
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        if (admissionKind != DataPullScopeAdmission.Kind.POST_CUTOVER) {
            throw new IllegalArgumentException("post-cutover evidence requires post admission");
        }
        String scope = DataPullScheduleAnchor.requireIdentity(scopeKey, "scopeKey", 96);
        LocalDateTime eligible = DataPullScopeAdmission.requireMillisecond(
                firstEligibleAtUtc,
                "firstEligibleAtUtc"
        );
        String bindingDigest = DataPullScopeAdmission.requireDigest(
                sourceBindingSha256,
                "sourceBindingSha256"
        );
        String cutover = DataPullScheduleAnchor.requireIdentity(
                cutoverKey,
                "cutoverKey",
                96
        );
        LocalDateTime frontier = DataPullScopeAdmission.requireMillisecond(
                reconcileAfterUtc,
                "reconcileAfterUtc"
        );
        MessageDigest digest = newDigest();
        update(digest, POST_CUTOVER_VERSION);
        update(digest, operation.name());
        update(digest, scope);
        update(digest, DataPullScheduleAnchor.Kind.POST_CUTOVER_SCOPE.name());
        update(digest, admissionKind.name());
        update(digest, epochMillis(eligible));
        update(digest, bindingDigest);
        update(digest, cutover);
        update(digest, epochMillis(frontier));
        return hex(digest.digest());
    }

    public static LocalDateTime postCutoverReconcileAfter(
            LocalDateTime firstEligibleAtUtc
    ) {
        return DataPullScopeAdmission.requireMillisecond(
                firstEligibleAtUtc,
                "firstEligibleAtUtc"
        );
    }

    private static String epochMillis(LocalDateTime value) {
        return Long.toString(value.toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "evidence value")
                .getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
