package com.nuono.next.datapull.orchestration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Opaque deterministic identity; callers never recover business fields by parsing it. */
public final class DataPullScopeKey {

    private DataPullScopeKey() {
    }

    public static String from(String namespace, String... stableParts) {
        String prefix = requirePart(namespace);
        StringBuilder canonical = new StringBuilder();
        append(canonical, prefix);
        for (String stablePart : stableParts) {
            append(canonical, requirePart(stablePart));
        }
        return prefix + "-" + hex(sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static void append(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value).append('|');
    }

    private static String requirePart(String value) {
        String nonNull = Objects.requireNonNull(value, "scope identity part");
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
            throw new IllegalArgumentException("scope identity parts must be stable non-blank values");
        }
        return nonNull;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 must be available", unavailable);
        }
    }

    private static String hex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            result[index * 2] = alphabet[value >>> 4];
            result[index * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(result);
    }
}
