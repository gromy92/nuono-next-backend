package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopeBindingDigest;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** One DB-bounded source item with its native keyset cursor and immutable payload proof. */
public final class ScheduleSourceScope {

    private final String sourceCursor;
    private final DataPullScope scope;
    private final DataPullScopeBindingCandidate binding;
    private final String immutablePayloadSha256;

    private ScheduleSourceScope(
            String sourceCursor,
            DataPullScope scope,
            DataPullScopeBindingCandidate binding
    ) {
        this.sourceCursor = requireText(sourceCursor, "sourceCursor", 512);
        this.scope = Objects.requireNonNull(scope, "scope");
        this.binding = binding;
        if (binding != null && !scope.getStableScopeKey().equals(binding.getScopeKey())) {
            throw new IllegalArgumentException("binding scope does not match source scope");
        }
        this.immutablePayloadSha256 = digest(scope, binding);
    }

    public static ScheduleSourceScope scope(String cursor, DataPullScope scope) {
        return new ScheduleSourceScope(cursor, scope, null);
    }

    public static ScheduleSourceScope bound(
            String cursor,
            DataPullScope scope,
            DataPullScopeBindingCandidate binding
    ) {
        return new ScheduleSourceScope(cursor, scope, Objects.requireNonNull(binding, "binding"));
    }

    public String getSourceCursor() { return sourceCursor; }
    public DataPullScope getScope() { return scope; }
    public DataPullScopeBindingCandidate getBinding() { return binding; }
    public String getImmutablePayloadSha256() { return immutablePayloadSha256; }

    private static String digest(DataPullScope scope, DataPullScopeBindingCandidate binding) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        update(digest, "DP_SCHEDULE_SOURCE_PAYLOAD_V1");
        update(digest, DataPullScopeBindingDigest.sha256(scope));
        digest.update((byte) (binding == null ? 0 : 1));
        if (binding != null) {
            update(digest, binding.getPayloadType());
            update(digest, binding.getPayloadSha256());
            update(digest, binding.getEffectiveFromUtc().toString());
        }
        return hex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "digest value")
                .getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }

    private static String requireText(String value, String field, int maxLength) {
        String text = Objects.requireNonNull(value, field);
        if (text.isEmpty() || !text.equals(text.trim()) || text.length() > maxLength
                || text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(field + " must fit its stable column");
        }
        return text;
    }
}
