package com.nuono.next.datapull.schedule;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Resumable ordered summary for one immutable source scan pass. */
public final class ScheduleSourceOrderedDigest {

    private static final String PREFIX = "DP_SCHEDULE_SOURCE_ORDER_V1";
    private final byte[] state;

    private ScheduleSourceOrderedDigest(byte[] state) {
        this.state = state.clone();
    }

    public static ScheduleSourceOrderedDigest initial() {
        return new ScheduleSourceOrderedDigest(hash(field(PREFIX)));
    }

    public static ScheduleSourceOrderedDigest resume(String lowercaseSha256) {
        return new ScheduleSourceOrderedDigest(parseDigest(lowercaseSha256));
    }

    public ScheduleSourceOrderedDigest append(
            String nativeSourceCursor,
            String scopeKey,
            String immutablePayloadSha256
    ) {
        byte[] payload = concat(
                state,
                field(requireText(nativeSourceCursor, "nativeSourceCursor")),
                field(requireText(scopeKey, "scopeKey")),
                field(parseDigest(immutablePayloadSha256))
        );
        return new ScheduleSourceOrderedDigest(hash(payload));
    }

    public String snapshot() {
        return hex(state);
    }

    private static byte[] field(String value) {
        return field(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] field(byte[] value) {
        return concat(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array(), value);
    }

    private static byte[] concat(byte[]... values) {
        int size = 0;
        for (byte[] value : values) size = Math.addExact(size, value.length);
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private static byte[] hash(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static byte[] parseDigest(String value) {
        String digest = requireText(value, "sha256");
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be lowercase hexadecimal");
        }
        byte[] result = new byte[32];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(
                    digest.substring(index * 2, index * 2 + 2), 16
            );
        }
        return result;
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }

    private static String requireText(String value, String field) {
        String text = Objects.requireNonNull(value, field);
        if (text.isEmpty() || !text.equals(text.trim()) || text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " must be a stable identity");
        }
        return text;
    }
}
