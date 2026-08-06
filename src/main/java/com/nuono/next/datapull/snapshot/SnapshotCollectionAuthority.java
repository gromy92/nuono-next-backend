package com.nuono.next.datapull.snapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/** Provider-native identity and extent for one immutable collection observation. */
public final class SnapshotCollectionAuthority {
    private static final int MAX_NATIVE_TOKEN_BYTES = 4_096;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public enum Kind {
        PAGED_GENERATION,
        COMPLETE_EXPORT,
        COMPLETE_RESPONSE,
        TWO_PASS_OBSERVATION
    }

    private final Kind kind;
    private final String generationTokenSha256;
    private final LocalDateTime providerAsOfUtc;
    private final long declaredCollectionCount;

    private SnapshotCollectionAuthority(
            Kind kind,
            String generationTokenSha256,
            LocalDateTime providerAsOfUtc,
            long declaredCollectionCount
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.generationTokenSha256 = requireDigest(generationTokenSha256);
        this.providerAsOfUtc = requireMillisecondPrecision(providerAsOfUtc);
        if (declaredCollectionCount < 0L) {
            throw new IllegalArgumentException("declaredCollectionCount must not be negative");
        }
        this.declaredCollectionCount = declaredCollectionCount;
    }

    /** Hashes an opaque token supplied by the provider; local timestamps are not authority. */
    public static SnapshotCollectionAuthority fromProviderToken(
            Kind kind,
            String providerNativeToken,
            LocalDateTime providerAsOfUtc,
            long declaredCollectionCount
    ) {
        if (kind == Kind.COMPLETE_RESPONSE || kind == Kind.TWO_PASS_OBSERVATION) {
            throw new IllegalArgumentException("authority kind is not provider-token based");
        }
        String token = Objects.requireNonNull(providerNativeToken, "providerNativeToken");
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        if (token.isEmpty() || !token.equals(token.trim()) || token.indexOf('\0') >= 0
                || bytes.length > MAX_NATIVE_TOKEN_BYTES) {
            throw new IllegalArgumentException("providerNativeToken must be stable and bounded");
        }
        return new SnapshotCollectionAuthority(
                kind,
                sha256(bytes),
                providerAsOfUtc,
                declaredCollectionCount
        );
    }

    /** Uses exact bytes from one structurally closed response as collection authority. */
    public static SnapshotCollectionAuthority fromCompleteResponse(
            byte[] exactResponseBytes,
            long exactRowCount
    ) {
        return new SnapshotCollectionAuthority(
                Kind.COMPLETE_RESPONSE,
                sha256(Objects.requireNonNull(exactResponseBytes, "exactResponseBytes")),
                null,
                exactRowCount
        );
    }

    /** Binds the recoverable digest produced by a completed two-pass multiset comparison. */
    public static SnapshotCollectionAuthority fromTwoPassObservation(
            String observationDigestSha256,
            long exactRowCount
    ) {
        return new SnapshotCollectionAuthority(
                Kind.TWO_PASS_OBSERVATION,
                observationDigestSha256,
                null,
                exactRowCount
        );
    }

    /** Restores an authority envelope from its validated persisted digest, never a raw token. */
    public static SnapshotCollectionAuthority fromPersistedDigest(
            Kind kind,
            String generationTokenSha256,
            LocalDateTime providerAsOfUtc,
            long declaredCollectionCount
    ) {
        return new SnapshotCollectionAuthority(
                kind,
                generationTokenSha256,
                providerAsOfUtc,
                declaredCollectionCount
        );
    }

    private static String requireDigest(String value) {
        String digest = Objects.requireNonNull(value, "generationTokenSha256");
        if (!SHA256.matcher(digest).matches()) {
            throw new IllegalArgumentException(
                    "generationTokenSha256 must be lowercase SHA-256"
            );
        }
        return digest;
    }

    private static LocalDateTime requireMillisecondPrecision(LocalDateTime value) {
        if (value != null && value.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException("providerAsOfUtc must fit millisecond precision");
        }
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                value.append(String.format("%02x", item & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public Kind getKind() {
        return kind;
    }

    public String getGenerationTokenSha256() {
        return generationTokenSha256;
    }

    public LocalDateTime getProviderAsOfUtc() {
        return providerAsOfUtc;
    }

    public long getDeclaredCollectionCount() {
        return declaredCollectionCount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SnapshotCollectionAuthority)) return false;
        SnapshotCollectionAuthority value = (SnapshotCollectionAuthority) other;
        return declaredCollectionCount == value.declaredCollectionCount
                && kind == value.kind
                && generationTokenSha256.equals(value.generationTokenSha256)
                && Objects.equals(providerAsOfUtc, value.providerAsOfUtc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                kind,
                generationTokenSha256,
                providerAsOfUtc,
                declaredCollectionCount
        );
    }
}
