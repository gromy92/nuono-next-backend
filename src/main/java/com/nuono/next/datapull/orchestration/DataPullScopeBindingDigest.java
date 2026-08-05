package com.nuono.next.datapull.orchestration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Versioned digest of the canonical scheduling identity, excluding physical binding row ids. */
public final class DataPullScopeBindingDigest {

    private static final String VERSION = "DP_SCOPE_BINDING_V1";

    private DataPullScopeBindingDigest() {
    }

    public static String sha256(DataPullScope scope) {
        DataPullScope value = Objects.requireNonNull(scope, "scope");
        MessageDigest digest = newDigest();
        update(digest, VERSION);
        update(digest, value.getNamespace());
        update(digest, Long.toString(value.getOwnerUserId()));
        updateNullable(digest, value.getLogicalStoreId() == null
                ? null : value.getLogicalStoreId().toString());
        update(digest, value.getAccountKey());
        updateNullable(digest, value.getProjectCode());
        updateNullable(digest, value.getStoreCode());
        updateNullable(digest, value.getSiteCode());
        updateNullable(digest, value.getEgressKey());
        return hex(digest.digest());
    }

    private static void updateNullable(MessageDigest digest, String value) {
        digest.update((byte) (value == null ? 0 : 1));
        if (value != null) {
            update(digest, value);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "digest value")
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
