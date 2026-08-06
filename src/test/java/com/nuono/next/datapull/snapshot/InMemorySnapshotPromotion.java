package com.nuono.next.datapull.snapshot;

import java.util.Objects;
import java.util.TreeMap;

/** Test-only state for extending a verified two-pass collection with trailing work pages. */
final class InMemorySnapshotPromotion<T> {
    private final SnapshotItemDescriptor<T> itemDescriptor;
    private final TreeMap<Integer, InMemorySnapshotPageRecord<T>> pages;
    private SnapshotCollectionAuthority authority;
    private Integer sourcePageCount;
    private Integer totalPageCount;

    InMemorySnapshotPromotion(
            SnapshotItemDescriptor<T> itemDescriptor,
            TreeMap<Integer, InMemorySnapshotPageRecord<T>> pages
    ) {
        this.itemDescriptor = Objects.requireNonNull(itemDescriptor, "itemDescriptor");
        this.pages = Objects.requireNonNull(pages, "pages");
    }

    SnapshotStagePromotionResult promote(
            int trailingPageCount,
            SnapshotPage.AuthorityMode authorityMode,
            Integer knownLastPage,
            SnapshotCollectionAuthority verifiedAuthority
    ) {
        if (trailingPageCount < 0
                || authorityMode != SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED
                || knownLastPage == null || verifiedAuthority == null) {
            return SnapshotStagePromotionResult.rejected("SNAPSHOT_TWO_PASS_NOT_VERIFIED");
        }
        int sourcePages = sourcePageCount == null ? knownLastPage : sourcePageCount;
        final int totalPages;
        try {
            totalPages = Math.addExact(sourcePages, trailingPageCount);
        } catch (ArithmeticException overflow) {
            return SnapshotStagePromotionResult.rejected("SNAPSHOT_TOTAL_PAGES_OVERFLOW");
        }
        if (sourcePageCount != null && !Objects.equals(totalPageCount, totalPages)) {
            return SnapshotStagePromotionResult.rejected("SNAPSHOT_PROMOTION_EXTENT_DRIFT");
        }
        sourcePageCount = sourcePages;
        totalPageCount = totalPages;
        authority = verifiedAuthority;
        return SnapshotStagePromotionResult.promoted(authority, sourcePages, totalPages);
    }

    SnapshotStageResult stage(SnapshotPage<T> page) {
        if (sourcePageCount == null || totalPageCount == null || authority == null || page == null) {
            return rejected("SNAPSHOT_TRAILING_PAGE_STATE_INVALID");
        }
        final InMemorySnapshotPageRecord<T> candidate;
        try {
            candidate = InMemorySnapshotPageRecord.from(page, itemDescriptor);
        } catch (RuntimeException invalid) {
            return rejected("SNAPSHOT_ITEM_DESCRIPTOR_INVALID");
        }
        if (!validIdentity(candidate)) return rejected("SNAPSHOT_TRAILING_PAGE_STATE_INVALID");
        InMemorySnapshotPageRecord<T> existing = pages.get(candidate.pageNo());
        if (existing != null) return replay(existing, candidate);
        if (candidate.pageNo() != pages.lastKey() + 1 || !validRouting(candidate)) {
            return rejected("SNAPSHOT_NON_CONTIGUOUS_TRAILING_PAGE");
        }
        pages.put(candidate.pageNo(), candidate);
        return SnapshotStageResult.staged(nextPage(candidate), totalPageCount);
    }

    private boolean validIdentity(InMemorySnapshotPageRecord<T> candidate) {
        return candidate.pageNo() > sourcePageCount
                && candidate.pageNo() <= totalPageCount
                && Objects.equals(candidate.totalPages(), totalPageCount)
                && candidate.authorityMode() == SnapshotPage.AuthorityMode.PROVIDER_AUTHORITY
                && Objects.equals(candidate.authority(), authority);
    }

    private boolean validRouting(InMemorySnapshotPageRecord<T> candidate) {
        boolean last = candidate.pageNo() == totalPageCount;
        return last == Boolean.TRUE.equals(candidate.lastPage())
                && (last ? candidate.nextPage() == null
                        : Objects.equals(candidate.nextPage(), candidate.pageNo() + 1));
    }

    private SnapshotStageResult replay(
            InMemorySnapshotPageRecord<T> existing,
            InMemorySnapshotPageRecord<T> candidate
    ) {
        if (!existing.sameMetadata(candidate)) return rejected("SNAPSHOT_PAGE_METADATA_DRIFT");
        if (!existing.sameContent(candidate)) return rejected("SNAPSHOT_PAGE_CONTENT_DRIFT");
        return SnapshotStageResult.idempotentReplay(nextPage(existing), totalPageCount);
    }

    private Integer nextPage(InMemorySnapshotPageRecord<T> page) {
        return page.pageNo() == totalPageCount ? null : page.pageNo() + 1;
    }

    private SnapshotStageResult rejected(String code) {
        return SnapshotStageResult.rejected(code);
    }
}
