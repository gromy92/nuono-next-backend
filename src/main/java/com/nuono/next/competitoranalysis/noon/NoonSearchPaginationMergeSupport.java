package com.nuono.next.competitoranalysis.noon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

final class NoonSearchPaginationMergeSupport {
    private static final int MAX_CROSS_PAGE_OVERLAP = 5;

    private NoonSearchPaginationMergeSupport() {
    }

    static NoonSearchPage merge(
            NoonSearchPage first,
            NoonSearchPage second,
            int requestedLimit
    ) {
        validateSecondPage(first, second);
        int expectedResultCount = Math.min(
                requestedLimit,
                first.getTotalHits() == null
                        ? requestedLimit
                        : first.getTotalHits()
        );
        int expectedFirstPageSlots = Math.min(
                NoonSearchPaginationSupport.PROVIDER_PAGE_LIMIT,
                expectedResultCount
        );
        int expectedSecondPageSlots = Math.max(
                0,
                expectedResultCount - expectedFirstPageSlots
        );
        requirePageSlotCount(first, expectedFirstPageSlots, "第一页");
        requirePageSlotCount(second, expectedSecondPageSlots, "第二页");

        NoonSearchResultAccumulator accumulator =
                new NoonSearchResultAccumulator();
        addScannedSlots(
                accumulator,
                first.getResults(),
                expectedFirstPageSlots
        );
        addScannedSlots(
                accumulator,
                second.getResults(),
                expectedSecondPageSlots
        );
        int overlapCount = expectedResultCount - accumulator.size();
        if (overlapCount > MAX_CROSS_PAGE_OVERLAP) {
            throw coverageFailure(
                    "Noon 前台搜索两页重叠商品过多：前 "
                            + expectedResultCount
                            + " 个排名位置中有 "
                            + overlapCount
                            + " 个跨页重复，不能确认稳定排名。",
                    second.getSourceUrl()
            );
        }

        NoonSearchPage merged = new NoonSearchPage();
        merged.setSourceUrl(joinSourceUrls(first, second));
        merged.setParserVersion(first.getParserVersion());
        merged.setProviderHttpStatus(second.getProviderHttpStatus());
        merged.setProviderPage(second.getProviderPage());
        merged.setProviderLimit(
                NoonSearchPaginationSupport.PROVIDER_PAGE_LIMIT
        );
        merged.setTotalHits(first.getTotalHits());
        merged.setTotalPages(first.getTotalPages());
        merged.setResponseHash(combinedHash(first, second));
        merged.setCapturedAt(latestCapturedAt(first, second));
        merged.setResults(accumulator.results());
        merged.setCoverageComplete(true);
        return merged;
    }

    private static void validateSecondPage(
            NoonSearchPage first,
            NoonSearchPage second
    ) {
        if (first == null) {
            throw coverageFailure(
                    "Noon 前台搜索第一页未返回，前 200 扫描失败。",
                    null
            );
        }
        if (second == null) {
            throw coverageFailure(
                    "Noon 前台搜索第二页未返回，前 200 扫描失败。",
                    first.getSourceUrl()
            );
        }
        if (second.getProviderPage() == null
                || second.getProviderPage() != 2) {
            throw coverageFailure(
                    "Noon 前台搜索第二页页码无法确认，不能写入前 200 排名。",
                    second.getSourceUrl()
            );
        }
        if (second.getProviderLimit() == null
                || second.getProviderLimit()
                != NoonSearchPaginationSupport.PROVIDER_PAGE_LIMIT) {
            throw coverageFailure(
                    "Noon 前台搜索第二页未确认按每页 100 条返回，不能写入前 200 排名。",
                    second.getSourceUrl()
            );
        }
        /*
         * Noon 的列表总量会在两次独立请求之间变化，第二页也可能省略
         * nbHits/nbPages。完整性由第一页总量、页码、每页实际排名槽位，
         * 以及受限的跨页重叠共同保证。
         */
        if (first.getTotalHits() != null
                && first.getTotalHits()
                > NoonSearchPaginationSupport.PROVIDER_PAGE_LIMIT
                && (second.getResults() == null
                || second.getResults().isEmpty())) {
            throw coverageFailure(
                    "Noon 前台搜索声明存在第二页，但第二页没有商品结果。",
                    second.getSourceUrl()
            );
        }
    }

    private static void addScannedSlots(
            NoonSearchResultAccumulator accumulator,
            List<NoonSearchResult> results,
            int slotCount
    ) {
        if (results == null || slotCount <= 0) {
            return;
        }
        int limit = Math.min(slotCount, results.size());
        for (int index = 0; index < limit; index++) {
            accumulator.addScannedSlot(results.get(index));
        }
    }

    private static void requirePageSlotCount(
            NoonSearchPage page,
            int expected,
            String pageLabel
    ) {
        int actual = page == null || page.getResults() == null
                ? 0
                : page.getResults().size();
        if (actual < expected) {
            throw coverageFailure(
                    "Noon 前台搜索"
                            + pageLabel
                            + "排名位置不足：应返回 "
                            + expected
                            + " 个，实际仅解析 "
                            + actual
                            + " 个。",
                    page == null ? null : page.getSourceUrl()
            );
        }
    }

    private static String joinSourceUrls(
            NoonSearchPage first,
            NoonSearchPage second
    ) {
        return first.getSourceUrl() != null
                ? first.getSourceUrl()
                : second.getSourceUrl();
    }

    private static LocalDateTime latestCapturedAt(
            NoonSearchPage first,
            NoonSearchPage second
    ) {
        LocalDateTime firstValue = first.getCapturedAt();
        LocalDateTime secondValue = second.getCapturedAt();
        if (firstValue == null) {
            return secondValue;
        }
        if (secondValue == null || firstValue.isAfter(secondValue)) {
            return firstValue;
        }
        return secondValue;
    }

    private static String combinedHash(
            NoonSearchPage first,
            NoonSearchPage second
    ) {
        String value = String.valueOf(first.getResponseHash())
                + "|"
                + String.valueOf(second.getResponseHash());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                output.append(String.format(
                        Locale.ROOT,
                        "%02x",
                        item & 0xff
                ));
            }
            return output.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Noon 搜索分页响应 hash 计算失败。",
                    exception
            );
        }
    }

    private static NoonSearchProviderException coverageFailure(
            String message,
            String sourceUrl
    ) {
        return new NoonSearchProviderException(
                "SCAN_COVERAGE_INCOMPLETE",
                message,
                null,
                sourceUrl,
                null
        );
    }
}
