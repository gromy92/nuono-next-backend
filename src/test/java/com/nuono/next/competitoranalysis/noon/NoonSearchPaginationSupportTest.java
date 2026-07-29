package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonSearchPaginationSupportTest {

    @Test
    void mergesTwoVerifiedPagesIntoTop200RankPositions() {
        NoonSearchPage first = page(1, 300, 3, 1);
        NoonSearchPage second = page(101, 300, 3, 2);

        assertTrue(
                NoonSearchPaginationSupport.needsSecondPage(first, 200)
        );
        NoonSearchPage merged = NoonSearchPaginationSupport.merge(
                first,
                second,
                200
        );

        assertTrue(merged.isCoverageComplete());
        assertEquals(200, merged.getResults().size());
        assertEquals(
                "N00000200",
                merged.getResults().get(199).getNoonProductCode()
        );
        assertEquals(
                200,
                merged.getResults().get(199).getRankPosition()
        );
    }

    @Test
    void acceptsVerifiedCoverageWhenLiveTotalsDriftBetweenPages() {
        NoonSearchPage first = page(1, 300, 3, 1);
        NoonSearchPage second = page(101, 297, 4, 2);

        NoonSearchPage merged = NoonSearchPaginationSupport.merge(
                first,
                second,
                200
        );

        assertTrue(merged.isCoverageComplete());
        assertEquals(200, merged.getResults().size());
        assertEquals(300, merged.getTotalHits());
        assertEquals(3, merged.getTotalPages());
    }

    @Test
    void acceptsVerifiedCoverageWhenSecondPageOmitsLiveTotals() {
        NoonSearchPage first = page(1, 300, 3, 1);
        NoonSearchPage second = page(101, 300, 3, 2);
        second.setTotalHits(null);
        second.setTotalPages(null);

        NoonSearchPage merged = NoonSearchPaginationSupport.merge(
                first,
                second,
                200
        );

        assertTrue(merged.isCoverageComplete());
        assertEquals(200, merged.getResults().size());
    }

    @Test
    void refusesToClaimTop200WithoutProviderCoverageMetadata() {
        NoonSearchPage first = page(1, 300, 3, 1);
        first.setTotalHits(null);

        NoonSearchProviderException error = assertThrows(
                NoonSearchProviderException.class,
                () -> NoonSearchPaginationSupport.needsSecondPage(first, 200)
        );

        assertEquals("SCAN_COVERAGE_INCOMPLETE", error.getErrorCode());
    }

    @Test
    void refusesASecondPageWhoseProviderPageIsNotTwo() {
        NoonSearchPage first = page(1, 300, 3, 1);
        NoonSearchPage second = page(101, 300, 3, 1);

        NoonSearchProviderException error = assertThrows(
                NoonSearchProviderException.class,
                () -> NoonSearchPaginationSupport.merge(first, second, 200)
        );

        assertEquals("SCAN_COVERAGE_INCOMPLETE", error.getErrorCode());
    }

    @Test
    void refusesProviderPageSizeThatCannotCoverTop200() {
        NoonSearchPage first = page(1, 300, 3, 1);
        first.setProviderLimit(50);

        NoonSearchProviderException error = assertThrows(
                NoonSearchProviderException.class,
                () -> NoonSearchPaginationSupport.needsSecondPage(first, 200)
        );

        assertEquals("SCAN_COVERAGE_INCOMPLETE", error.getErrorCode());
    }

    @Test
    void acceptsSmallCrossPageOverlapAndPreservesScannedRankSlots() {
        NoonSearchPage first = page(1, 300, 3, 1);
        NoonSearchPage second = page(99, 300, 3, 2);

        NoonSearchPage merged = NoonSearchPaginationSupport.merge(
                first,
                second,
                200
        );

        assertTrue(merged.isCoverageComplete());
        assertEquals(198, merged.getResults().size());
        assertEquals(
                103,
                find(merged, "N00000101").getRankPosition()
        );
        assertEquals(
                200,
                find(merged, "N00000198").getRankPosition()
        );
    }

    @Test
    void refusesExcessiveCrossPageOverlap() {
        NoonSearchPage first = page(1, 300, 3, 1);
        NoonSearchPage second = page(51, 300, 3, 2);

        NoonSearchProviderException error = assertThrows(
                NoonSearchProviderException.class,
                () -> NoonSearchPaginationSupport.merge(first, second, 200)
        );

        assertEquals("SCAN_COVERAGE_INCOMPLETE", error.getErrorCode());
    }

    @Test
    void refusesASecondPageWithFewerThanOneHundredRankSlots() {
        NoonSearchPage first = page(1, 300, 3, 1);
        NoonSearchPage second = page(101, 300, 3, 2);
        second.setResults(
                new ArrayList<>(
                        second.getResults().subList(0, 99)
                )
        );

        NoonSearchProviderException error = assertThrows(
                NoonSearchProviderException.class,
                () -> NoonSearchPaginationSupport.merge(first, second, 200)
        );

        assertEquals("SCAN_COVERAGE_INCOMPLETE", error.getErrorCode());
    }

    @Test
    void acceptsExplicitZeroHitsWithoutPaginationMetadata() {
        NoonSearchPage first = page(1, 0, 0, 1);
        first.setProviderPage(null);
        first.setProviderLimit(null);
        first.setTotalPages(null);
        first.setResults(List.of());

        assertTrue(
                !NoonSearchPaginationSupport.needsSecondPage(first, 200)
        );
    }

    private static NoonSearchPage page(
            int firstCode,
            int totalHits,
            int totalPages,
            int providerPage
    ) {
        NoonSearchPage page = new NoonSearchPage();
        page.setSourceUrl("https://www.noon.com/search?page=" + providerPage);
        page.setParserVersion("fixture-v1");
        page.setProviderHttpStatus(200);
        page.setProviderPage(providerPage);
        page.setProviderLimit(100);
        page.setTotalHits(totalHits);
        page.setTotalPages(totalPages);
        page.setResponseHash("hash-" + providerPage);
        page.setCapturedAt(LocalDateTime.parse("2026-07-29T00:00:00"));
        List<NoonSearchResult> results = new ArrayList<>();
        for (int index = firstCode; index < firstCode + 100; index++) {
            NoonSearchResult result = new NoonSearchResult();
            result.setNoonProductCode(String.format("N%08d", index));
            result.setPosition(index - firstCode + 1);
            result.setRankPosition(index - firstCode + 1);
            results.add(result);
        }
        page.setResults(results);
        return page;
    }

    private static NoonSearchResult find(
            NoonSearchPage page,
            String productCode
    ) {
        return page.getResults().stream()
                .filter(result -> productCode.equals(
                        result.getNoonProductCode()
                ))
                .findFirst()
                .orElseThrow();
    }
}
