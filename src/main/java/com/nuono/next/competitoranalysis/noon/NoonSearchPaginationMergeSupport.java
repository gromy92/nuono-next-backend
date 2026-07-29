package com.nuono.next.competitoranalysis.noon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Locale;

final class NoonSearchPaginationMergeSupport {
    private static final int MAX_DUPLICATE_RANK_SLOTS = 5;

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
        int firstOrganicSlots = channelSlotCount(
                first,
                false
        );
        int firstSponsoredSlots = channelSlotCount(
                first,
                true
        );
        addPageSlots(
                accumulator,
                first,
                0,
                0,
                0,
                expectedFirstPageSlots
        );
        addPageSlots(
                accumulator,
                second,
                expectedFirstPageSlots,
                firstOrganicSlots,
                firstSponsoredSlots,
                expectedSecondPageSlots
        );
        int duplicateSlotCount =
                expectedResultCount - accumulator.size();
        if (duplicateSlotCount > MAX_DUPLICATE_RANK_SLOTS) {
            throw coverageFailure(
                    "Noon 前台搜索重复排名槽位过多：前 "
                            + expectedResultCount
                            + " 个排名位置中有 "
                            + duplicateSlotCount
                            + " 个重复，不能确认稳定排名。",
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
        merged.setProviderResultSlotCount(expectedResultCount);
        merged.setProviderOrganicSlotCount(
                firstOrganicSlots + channelSlotCount(second, false)
        );
        merged.setProviderSponsoredSlotCount(
                firstSponsoredSlots + channelSlotCount(second, true)
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

    private static void addPageSlots(
            NoonSearchResultAccumulator accumulator,
            NoonSearchPage page,
            int positionOffset,
            int organicRankOffset,
            int sponsoredRankOffset,
            int slotCount
    ) {
        if (page == null || slotCount <= 0) {
            return;
        }
        for (NoonSearchResult result : page.getResults()) {
            Integer position = result == null
                    ? null
                    : result.getPosition();
            if (position != null && position > slotCount) {
                continue;
            }
            accumulator.addPreservingRankSlot(
                    result,
                    positionOffset,
                    organicRankOffset,
                    sponsoredRankOffset
            );
        }
    }

    private static void requirePageSlotCount(
            NoonSearchPage page,
            int expected,
            String pageLabel
    ) {
        int actual = pageSlotCount(page);
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

    private static int pageSlotCount(NoonSearchPage page) {
        if (page == null) {
            return 0;
        }
        Integer count = page.getProviderResultSlotCount();
        return count == null ? page.getResults().size() : count;
    }

    private static int channelSlotCount(
            NoonSearchPage page,
            boolean sponsored
    ) {
        if (page == null) {
            return 0;
        }
        Integer count = sponsored
                ? page.getProviderSponsoredSlotCount()
                : page.getProviderOrganicSlotCount();
        if (count != null) {
            return count;
        }
        return page.getResults().stream()
                .filter(result -> result != null
                        && result.isSponsored() == sponsored)
                .map(NoonSearchResult::getRankPosition)
                .filter(value -> value != null && value > 0)
                .max(Integer::compareTo)
                .orElse(0);
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
