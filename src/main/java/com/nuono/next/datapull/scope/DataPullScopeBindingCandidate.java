package com.nuono.next.datapull.scope;

import com.nuono.next.datapull.runtime.OperationCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** One immutable execution-scope payload proposed by the live scope catalog. */
public final class DataPullScopeBindingCandidate {
    static final int PAYLOAD_MAX_BYTES = 16_711_680;

    private final String bindingId;
    private final OperationCode operationCode;
    private final String scopeKey;
    private final String payloadType;
    private final String payloadSha256;
    private final String payload;
    private final LocalDateTime effectiveFromUtc;

    public DataPullScopeBindingCandidate(
            OperationCode operationCode,
            String scopeKey,
            String payloadType,
            String payload,
            LocalDateTime effectiveFromUtc
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.scopeKey = requireIdentity(scopeKey, "scopeKey", 96);
        this.payloadType = requireIdentity(payloadType, "payloadType", 64);
        this.payload = requirePayload(payload);
        this.effectiveFromUtc = requireMillisecond(effectiveFromUtc, "effectiveFromUtc");
        this.payloadSha256 = sha256(this.payload);
        this.bindingId = sha256(lengthPrefixed(operationCode.name())
                + lengthPrefixed(this.scopeKey)
                + lengthPrefixed(this.effectiveFromUtc.toString())
                + lengthPrefixed(this.payloadType)
                + lengthPrefixed(this.payloadSha256));
    }

    static String requireIdentity(String value, String field, int maxLength) {
        String nonNull = Objects.requireNonNull(value, field);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())
                || nonNull.length() > maxLength || nonNull.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " must fit its stable identity column");
        }
        return nonNull;
    }

    static LocalDateTime requireMillisecond(LocalDateTime value, String field) {
        LocalDateTime nonNull = Objects.requireNonNull(value, field);
        if (!nonNull.equals(nonNull.truncatedTo(ChronoUnit.MILLIS))) {
            throw new IllegalArgumentException(field + " must fit DATETIME(3)");
        }
        return nonNull;
    }

    static String requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    static String requirePayload(String value) {
        String nonNull = Objects.requireNonNull(value, "payload");
        int bytes = nonNull.getBytes(StandardCharsets.UTF_8).length;
        if (nonNull.isEmpty() || bytes > PAYLOAD_MAX_BYTES || nonNull.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("scope payload must fit its immutable MEDIUMTEXT");
        }
        return nonNull;
    }

    static String sha256(String value) {
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

    private static String lengthPrefixed(String value) {
        return value.length() + ":" + value + "|";
    }

    public String getBindingId() { return bindingId; }
    public OperationCode getOperationCode() { return operationCode; }
    public String getScopeKey() { return scopeKey; }
    public String getPayloadType() { return payloadType; }
    public String getPayloadSha256() { return payloadSha256; }
    public String getPayload() { return payload; }
    public LocalDateTime getEffectiveFromUtc() { return effectiveFromUtc; }
}
