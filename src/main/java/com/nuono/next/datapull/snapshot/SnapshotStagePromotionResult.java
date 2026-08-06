package com.nuono.next.datapull.snapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Result of atomically extending one verified two-pass collection with trailing work pages. */
public final class SnapshotStagePromotionResult {
    private final String sanitizedCode;
    private final SnapshotCollectionAuthority authority;
    private final Integer sourcePageCount;
    private final Integer totalPageCount;

    private SnapshotStagePromotionResult(
            String sanitizedCode,
            SnapshotCollectionAuthority authority,
            Integer sourcePageCount,
            Integer totalPageCount
    ) {
        this.sanitizedCode = sanitizedCode;
        this.authority = authority;
        this.sourcePageCount = sourcePageCount;
        this.totalPageCount = totalPageCount;
    }

    public static SnapshotStagePromotionResult promoted(
            SnapshotCollectionAuthority authority,
            int sourcePageCount,
            int totalPageCount
    ) {
        if (sourcePageCount < 1 || totalPageCount < sourcePageCount) {
            throw new IllegalArgumentException("snapshot promotion page counts are invalid");
        }
        return new SnapshotStagePromotionResult(
                null,
                Objects.requireNonNull(authority, "authority"),
                sourcePageCount,
                totalPageCount
        );
    }

    public static SnapshotStagePromotionResult rejected(String sanitizedCode) {
        return new SnapshotStagePromotionResult(
                Objects.requireNonNull(sanitizedCode, "sanitizedCode"),
                null,
                null,
                null
        );
    }

    public boolean isPromoted() { return sanitizedCode == null; }
    public String getSanitizedCode() { return sanitizedCode; }
    public Optional<SnapshotCollectionAuthority> getAuthority() {
        return Optional.ofNullable(authority);
    }
    public OptionalInt getSourcePageCount() {
        return sourcePageCount == null
                ? OptionalInt.empty()
                : OptionalInt.of(sourcePageCount);
    }
    public OptionalInt getTotalPageCount() {
        return totalPageCount == null
                ? OptionalInt.empty()
                : OptionalInt.of(totalPageCount);
    }
}
