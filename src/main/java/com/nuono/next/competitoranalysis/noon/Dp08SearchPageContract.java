package com.nuono.next.competitoranalysis.noon;

import java.time.Duration;
import java.time.LocalDateTime;

/** Strict DP-08 page envelope and merge contract. */
public final class Dp08SearchPageContract {
    public static final int RANK_PAGE_SIZE = 100;
    public static final int RANK_PAGE_COUNT = 2;
    public static final int RANK_SCAN_DEPTH = 200;
    static final Duration MAX_RANK_PAGE_CAPTURE_GAP = Duration.ofMinutes(2);

    private Dp08SearchPageContract() {
    }

    public static NoonSearchPage requireRankPage(NoonSearchPage page, int expectedPage) {
        if (expectedPage < 1 || expectedPage > RANK_PAGE_COUNT) {
            throw new IllegalArgumentException("DP-08-A page must be 1 or 2");
        }
        if (page == null) {
            throw contract("DP08_RANK_PAGE_MISSING", null);
        }
        if (page.getProviderPage() == null || page.getProviderPage() != expectedPage) {
            throw contract("DP08_RANK_PAGE_NUMBER_INVALID", page);
        }
        if (page.getProviderLimit() == null || page.getProviderLimit() != RANK_PAGE_SIZE) {
            throw contract("DP08_RANK_PAGE_LIMIT_INVALID", page);
        }
        if (slotCount(page) != RANK_PAGE_SIZE) {
            throw contract("DP08_RANK_PAGE_SLOT_COUNT_INVALID", page);
        }
        if (expectedPage == 1
                && (page.getTotalHits() == null || page.getTotalHits() < RANK_SCAN_DEPTH)) {
            throw contract("DP08_RANK_TOP200_UNAVAILABLE", page);
        }
        if (expectedPage == 1
                && (page.getTotalPages() == null || page.getTotalPages() < RANK_PAGE_COUNT)) {
            throw contract("DP08_RANK_SECOND_PAGE_UNAVAILABLE", page);
        }
        return page;
    }

    public static NoonSearchPage mergeRankPages(NoonSearchPage first, NoonSearchPage second) {
        requireRankPage(first, 1);
        requireRankPage(second, 2);
        requireSingleCaptureWindow(first, second);
        NoonSearchPage merged = NoonSearchPaginationMergeSupport.merge(
                first, second, RANK_SCAN_DEPTH
        );
        if (!merged.isCoverageComplete()
                || slotCount(merged) != RANK_SCAN_DEPTH) {
            throw contract("DP08_RANK_COVERAGE_INCOMPLETE", merged);
        }
        return merged;
    }

    private static void requireSingleCaptureWindow(
            NoonSearchPage first,
            NoonSearchPage second
    ) {
        LocalDateTime firstCapturedAt = first.getCapturedAt();
        LocalDateTime secondCapturedAt = second.getCapturedAt();
        if (firstCapturedAt == null || secondCapturedAt == null) {
            throw contract("DP08_RANK_CAPTURE_TIME_MISSING", second);
        }
        Duration gap = Duration.between(firstCapturedAt, secondCapturedAt);
        if (gap.isNegative()) {
            throw contract("DP08_RANK_CAPTURE_TIME_REVERSED", second);
        }
        if (gap.compareTo(MAX_RANK_PAGE_CAPTURE_GAP) > 0) {
            throw contract("DP08_RANK_CAPTURE_WINDOW_EXCEEDED", second);
        }
    }

    private static int slotCount(NoonSearchPage page) {
        Integer count = page.getProviderResultSlotCount();
        return count == null ? page.getResults().size() : count;
    }

    private static NoonSearchProviderException contract(String code, NoonSearchPage page) {
        return new NoonSearchProviderException(
                code,
                code,
                page == null ? null : page.getProviderHttpStatus(),
                page == null ? null : page.getSourceUrl(),
                page == null ? null : page.getResponseHash()
        );
    }
}
