package com.nuono.next.datapull.snapshot;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** One structurally parsed provider page and its optional pagination evidence. */
public final class SnapshotPage<T> {
    public enum AuthorityMode {
        PROVIDER_AUTHORITY,
        TWO_PASS_REQUIRED
    }

    private final int pageNo;
    private final Integer nextPage;
    private final Boolean lastPage;
    private final Integer totalPages;
    private final List<T> items;
    private final SnapshotCollectionAuthority authority;
    private final AuthorityMode authorityMode;
    private final int sourceItemCount;
    private final int businessSkippedItemCount;
    private final List<String> businessSkippedComparisonFingerprints;

    public SnapshotPage(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            List<T> items
    ) {
        this(
                pageNo,
                nextPage,
                lastPage,
                totalPages,
                items,
                null,
                AuthorityMode.PROVIDER_AUTHORITY,
                requireItems(items).size(),
                0,
                List.of()
        );
    }

    public SnapshotPage(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            List<T> items,
            SnapshotCollectionAuthority authority,
            int sourceItemCount,
            int businessSkippedItemCount
    ) {
        this(
                pageNo, nextPage, lastPage, totalPages, items, authority,
                AuthorityMode.PROVIDER_AUTHORITY, sourceItemCount,
                businessSkippedItemCount, List.of()
        );
    }

    public static <T> SnapshotPage<T> twoPassRequired(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            List<T> items,
            int sourceItemCount,
            int businessSkippedItemCount
    ) {
        return twoPassRequired(
                pageNo, nextPage, lastPage, totalPages, items,
                sourceItemCount, businessSkippedItemCount, List.of()
        );
    }

    public static <T> SnapshotPage<T> twoPassRequired(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            List<T> items,
            int sourceItemCount,
            int businessSkippedItemCount,
            List<String> businessSkippedComparisonFingerprints
    ) {
        return new SnapshotPage<>(
                pageNo, nextPage, lastPage, totalPages, items, null,
                AuthorityMode.TWO_PASS_REQUIRED, sourceItemCount,
                businessSkippedItemCount, businessSkippedComparisonFingerprints
        );
    }

    private SnapshotPage(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            List<T> items,
            SnapshotCollectionAuthority authority,
            AuthorityMode authorityMode,
            int sourceItemCount,
            int businessSkippedItemCount,
            List<String> businessSkippedComparisonFingerprints
    ) {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be positive");
        }
        if (nextPage != null && nextPage < 1) {
            throw new IllegalArgumentException("nextPage must be positive when present");
        }
        if (totalPages != null && totalPages < 1) {
            throw new IllegalArgumentException("totalPages must be positive when present");
        }
        this.pageNo = pageNo;
        this.nextPage = nextPage;
        this.lastPage = lastPage;
        this.totalPages = totalPages;
        this.items = List.copyOf(requireItems(items));
        if (sourceItemCount < 0 || businessSkippedItemCount < 0
                || sourceItemCount != Math.addExact(
                        this.items.size(), businessSkippedItemCount
                )) {
            throw new IllegalArgumentException("snapshot source item accounting is invalid");
        }
        this.authorityMode = Objects.requireNonNull(authorityMode, "authorityMode");
        if (authorityMode == AuthorityMode.TWO_PASS_REQUIRED && authority != null) {
            throw new IllegalArgumentException("two-pass pages must not carry provider authority");
        }
        this.authority = authority;
        this.sourceItemCount = sourceItemCount;
        this.businessSkippedItemCount = businessSkippedItemCount;
        this.businessSkippedComparisonFingerprints = List.copyOf(Objects.requireNonNull(
                businessSkippedComparisonFingerprints,
                "businessSkippedComparisonFingerprints"
        ));
        if (authorityMode == AuthorityMode.TWO_PASS_REQUIRED
                && this.businessSkippedComparisonFingerprints.size()
                != businessSkippedItemCount) {
            throw new IllegalArgumentException(
                    "two-pass skipped rows require one comparison fingerprint each"
            );
        }
    }

    public int getPageNo() {
        return pageNo;
    }

    public OptionalInt getNextPage() {
        return nextPage == null ? OptionalInt.empty() : OptionalInt.of(nextPage);
    }

    public Optional<Boolean> getLastPage() {
        return Optional.ofNullable(lastPage);
    }

    public OptionalInt getTotalPages() {
        return totalPages == null ? OptionalInt.empty() : OptionalInt.of(totalPages);
    }

    public List<T> getItems() {
        return items;
    }

    public Optional<SnapshotCollectionAuthority> getAuthority() {
        return Optional.ofNullable(authority);
    }

    public AuthorityMode getAuthorityMode() {
        return authorityMode;
    }

    public int getSourceItemCount() {
        return sourceItemCount;
    }

    public int getBusinessSkippedItemCount() {
        return businessSkippedItemCount;
    }

    public List<String> getBusinessSkippedComparisonFingerprints() {
        return businessSkippedComparisonFingerprints;
    }

    private static <T> List<T> requireItems(List<T> items) {
        return Objects.requireNonNull(items, "items");
    }
}
