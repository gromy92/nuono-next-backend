package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.RiskShareLevel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Canonical, secret-free key for exact, account, and verified-exit holds. */
public final class DataPullBackoffHoldKey {

    private DataPullBackoffHoldKey() {
    }

    public static String from(RiskShareLevel shareLevel, DataPullBackoffIdentity identity) {
        RiskShareLevel level = Objects.requireNonNull(shareLevel, "shareLevel");
        DataPullBackoffIdentity value = Objects.requireNonNull(identity, "identity");
        String canonical;
        if (level == RiskShareLevel.EXACT) {
            canonical = parts(
                    level.name(),
                    value.getProviderChannel(),
                    value.getAccountKey(),
                    value.getOperationCode().name(),
                    value.getScopeKey()
            );
        } else if (level == RiskShareLevel.ACCOUNT) {
            canonical = parts(
                    level.name(),
                    value.getProviderChannel(),
                    value.getAccountKey()
            );
        } else if (level == RiskShareLevel.EXIT) {
            canonical = parts(
                    level.name(),
                    value.getProviderChannel(),
                    value.requireEgressKey()
            );
        } else {
            throw new IllegalArgumentException("unsupported risk share level: " + level);
        }
        return hex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static String parts(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            String nonNull = Objects.requireNonNull(value, "backoff identity part");
            if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
                throw new IllegalArgumentException("backoff identities must be stable non-blank values");
            }
            builder.append(nonNull.length()).append(':').append(nonNull).append('|');
        }
        return builder.toString();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 must be available", unavailable);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }
}
