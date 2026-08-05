package com.nuono.next.datapull.snapshot;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/** Complete ordered snapshot evidence or one fail-closed proof code. */
public final class SnapshotStageProof<T> {
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");
    private final boolean complete;
    private final String sanitizedCode;
    private final Integer lastPage;
    private final List<T> items;
    private final long appliedItemCount;
    private final int skippedIdentityCount;
    private final long businessSkippedItemCount;
    private final long sourceItemCount;
    private final SnapshotCollectionAuthority authority;

    private SnapshotStageProof(
            boolean complete,
            String sanitizedCode,
            Integer lastPage,
            List<T> items,
            long appliedItemCount,
            int skippedIdentityCount,
            long businessSkippedItemCount,
            long sourceItemCount,
            SnapshotCollectionAuthority authority
    ) {
        this.complete = complete;
        this.sanitizedCode = sanitizedCode;
        this.lastPage = lastPage;
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.appliedItemCount = appliedItemCount;
        this.skippedIdentityCount = skippedIdentityCount;
        this.businessSkippedItemCount = businessSkippedItemCount;
        this.sourceItemCount = sourceItemCount;
        this.authority = authority;
    }

    public static <T> SnapshotStageProof<T> complete(
            int lastPage,
            List<T> items,
            int skippedIdentityCount
    ) {
        List<T> values = List.copyOf(Objects.requireNonNull(items, "items"));
        return complete(
                lastPage,
                values,
                skippedIdentityCount,
                0,
                Math.addExact(values.size(), skippedIdentityCount),
                null
        );
    }

    public static <T> SnapshotStageProof<T> complete(
            int lastPage,
            List<T> items,
            int skippedIdentityCount,
            long businessSkippedItemCount,
            long sourceItemCount,
            SnapshotCollectionAuthority authority
    ) {
        if (lastPage < 1) {
            throw new IllegalArgumentException("lastPage must be positive");
        }
        if (skippedIdentityCount < 0 || businessSkippedItemCount < 0
                || sourceItemCount < 0L) {
            throw new IllegalArgumentException("snapshot proof counts must not be negative");
        }
        List<T> values = List.copyOf(Objects.requireNonNull(items, "items"));
        long accounted = Math.addExact(
                Math.addExact((long) values.size(), skippedIdentityCount),
                businessSkippedItemCount
        );
        if (accounted != sourceItemCount) {
            throw new IllegalArgumentException("snapshot proof source accounting is invalid");
        }
        return new SnapshotStageProof<>(
                true,
                "SNAPSHOT_COMPLETE",
                lastPage,
                values,
                values.size(),
                skippedIdentityCount,
                businessSkippedItemCount,
                sourceItemCount,
                authority
        );
    }

    /** Metadata-only proof used by DP-04/07-A so APPLY never materializes the full container. */
    public static <T> SnapshotStageProof<T> completeMetadata(
            int lastPage,
            long appliedItemCount,
            int skippedIdentityCount,
            long businessSkippedItemCount,
            long sourceItemCount,
            SnapshotCollectionAuthority authority
    ) {
        if (lastPage < 1 || appliedItemCount < 0L || skippedIdentityCount < 0
                || businessSkippedItemCount < 0L || sourceItemCount < 0L) {
            throw new IllegalArgumentException("snapshot metadata proof counts are invalid");
        }
        long accounted = Math.addExact(
                Math.addExact(appliedItemCount, skippedIdentityCount),
                businessSkippedItemCount
        );
        if (accounted != sourceItemCount || authority == null
                || authority.getDeclaredCollectionCount() != sourceItemCount) {
            throw new IllegalArgumentException("snapshot metadata proof accounting is invalid");
        }
        return new SnapshotStageProof<>(
                true, "SNAPSHOT_COMPLETE", lastPage, List.of(), appliedItemCount,
                skippedIdentityCount, businessSkippedItemCount, sourceItemCount, authority
        );
    }

    public static <T> SnapshotStageProof<T> incomplete(String sanitizedCode) {
        return new SnapshotStageProof<>(
                false, requireCode(sanitizedCode), null, List.of(), 0L, 0, 0, 0L, null
        );
    }

    private static String requireCode(String code) {
        if (code == null
                || code.length() > 80
                || !code.equals(code.trim())
                || !SAFE_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("sanitizedCode must be a safe identity of at most 80 characters");
        }
        return code;
    }

    public boolean isComplete() {
        return complete;
    }

    public String getSanitizedCode() {
        return sanitizedCode;
    }

    public OptionalInt getLastPage() {
        return lastPage == null ? OptionalInt.empty() : OptionalInt.of(lastPage);
    }

    public List<T> getItems() {
        return items;
    }

    public long getAppliedItemCount() {
        return appliedItemCount;
    }

    public int getSkippedIdentityCount() {
        return skippedIdentityCount;
    }

    public long getBusinessSkippedItemCount() {
        return businessSkippedItemCount;
    }

    public long getSourceItemCount() {
        return sourceItemCount;
    }

    public Optional<SnapshotCollectionAuthority> getAuthority() {
        return Optional.ofNullable(authority);
    }
}
