package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.scope.DataPullScopeBindingEpoch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Validates and exposes the immutable supplemental scope payload copied into a task. */
public final class DataPullTaskScopeSnapshot {
    private DataPullTaskScopeSnapshot() {
    }

    public static String requirePayload(
            DataPullTask task,
            OperationCode operationCode,
            String payloadType
    ) {
        DataPullTask value = Objects.requireNonNull(task, "task");
        if (value.getOperationCode() != Objects.requireNonNull(operationCode, "operationCode")) {
            throw new IllegalStateException("task scope snapshot operation drift");
        }
        String expectedType = requireIdentity(payloadType, "payloadType", 64);
        requireDigest(value.getScopeBindingId(), "scopeBindingId");
        if (!expectedType.equals(value.getScopePayloadType())) {
            throw new IllegalStateException("task scope snapshot payload type drift");
        }
        requireDigest(value.getScopePayloadSha256(), "scopePayloadSha256");
        String payload = Objects.requireNonNull(value.getScopePayload(), "scopePayload");
        if (payload.isEmpty() || payload.getBytes(StandardCharsets.UTF_8).length > 16_711_680) {
            throw new IllegalStateException("task scope snapshot payload is invalid");
        }
        if (!sha256(payload).equals(value.getScopePayloadSha256())) {
            throw new IllegalStateException("task scope snapshot payload digest drift");
        }
        if (value.getScopeBindingEffectiveFromUtc() == null
                || value.getScheduleSlot() == null
                || value.getScheduleSlot().isBefore(value.getScopeBindingEffectiveFromUtc())) {
            throw new IllegalStateException("task schedule is outside its scope binding epoch");
        }
        return payload;
    }

    /** Test/adapter helper; production enqueue copies the same bytes with one INSERT...SELECT. */
    public static void bind(DataPullTask task, DataPullScopeBindingEpoch binding) {
        DataPullTask target = Objects.requireNonNull(task, "task");
        DataPullScopeBindingEpoch source = Objects.requireNonNull(binding, "binding");
        source.validate();
        if (target.getOperationCode() != source.getOperationCode()
                || !Objects.equals(target.getScopeKey(), source.getScopeKey())) {
            throw new IllegalArgumentException("binding does not identify the task scope");
        }
        target.setScopeBindingId(source.getBindingId());
        target.setScopePayloadType(source.getPayloadType());
        target.setScopePayloadSha256(source.getPayloadSha256());
        target.setScopePayload(source.getPayload());
        target.setScopeBindingEffectiveFromUtc(source.getEffectiveFromUtc());
    }

    private static String requireIdentity(String value, String field, int maxLength) {
        String nonNull = Objects.requireNonNull(value, field);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())
                || nonNull.length() > maxLength || nonNull.indexOf('\0') >= 0) {
            throw new IllegalStateException(field + " is not a stable identity");
        }
        return nonNull;
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException(field + " is not a SHA-256 digest");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
