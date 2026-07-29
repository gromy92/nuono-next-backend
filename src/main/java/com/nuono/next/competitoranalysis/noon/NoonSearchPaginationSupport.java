package com.nuono.next.competitoranalysis.noon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class NoonSearchPaginationSupport {
    static final int PROVIDER_PAGE_LIMIT = 100;
    static final int MAX_SCAN_DEPTH = 200;

    private NoonSearchPaginationSupport() {
    }

    static int requestedLimit(NoonSearchRequest request) {
        Integer value = request == null ? null : request.getLimit();
        return value == null
                ? 30
                : Math.max(1, Math.min(value, MAX_SCAN_DEPTH));
    }

    static NoonSearchRequest pageRequest(
            NoonSearchRequest source,
            int page,
            int pageLimit
    ) {
        return NoonSearchRequest.builder()
                .siteCode(source == null ? null : source.getSiteCode())
                .locale(source == null ? null : source.getLocale())
                .keyword(source == null ? null : source.getKeyword())
                .limit(Math.max(1, Math.min(pageLimit, PROVIDER_PAGE_LIMIT)))
                .page(page <= 1 ? null : page)
                .build();
    }

    static boolean needsSecondPage(NoonSearchPage first, int requestedLimit) {
        if (requestedLimit <= PROVIDER_PAGE_LIMIT) {
            return false;
        }
        if (first == null) {
            throw coverageFailure("Noon 前台搜索第一页未返回。", null);
        }
        if (Integer.valueOf(0).equals(first.getTotalHits())) {
            if (!first.getResults().isEmpty()) {
                throw coverageFailure(
                        "Noon 前台搜索声明总结果为 0，但同时返回了商品。",
                        first.getSourceUrl()
                );
            }
            return false;
        }
        requireFirstPageCoverageMetadata(first);
        if (first.getTotalHits() <= PROVIDER_PAGE_LIMIT) {
            requireCoveredResultCount(
                    uniqueProductCount(first.getResults()),
                    first.getTotalHits(),
                    first.getSourceUrl()
            );
            return false;
        }
        if (first.getTotalPages() < 2) {
            throw coverageFailure(
                    "Noon 前台搜索总结果数超过 100，但总页数不足 2，不能确认前 200 覆盖。",
                    first.getSourceUrl()
            );
        }
        return true;
    }

    static NoonSearchPage merge(
            NoonSearchPage first,
            NoonSearchPage second,
            int requestedLimit
    ) {
        validateSecondPage(first, second);
        NoonSearchResultAccumulator accumulator = new NoonSearchResultAccumulator();
        add(accumulator, first == null ? null : first.getResults());
        add(accumulator, second == null ? null : second.getResults());
        int expectedResultCount = Math.min(
                requestedLimit,
                first == null || first.getTotalHits() == null
                        ? requestedLimit
                        : first.getTotalHits()
        );
        requireCoveredResultCount(
                uniqueProductCount(accumulator.results()),
                expectedResultCount,
                second == null ? null : second.getSourceUrl()
        );

        NoonSearchPage merged = new NoonSearchPage();
        merged.setSourceUrl(joinSourceUrls(first, second));
        merged.setParserVersion(first == null ? null : first.getParserVersion());
        merged.setProviderHttpStatus(second == null
                ? first == null ? null : first.getProviderHttpStatus()
                : second.getProviderHttpStatus());
        merged.setProviderPage(second == null ? 1 : second.getProviderPage());
        merged.setProviderLimit(PROVIDER_PAGE_LIMIT);
        merged.setTotalHits(first == null ? null : first.getTotalHits());
        merged.setTotalPages(first == null ? null : first.getTotalPages());
        merged.setResponseHash(combinedHash(first, second));
        merged.setCapturedAt(latestCapturedAt(first, second));
        merged.setResults(accumulator.results());
        merged.setCoverageComplete(true);
        return merged;
    }

    static NoonSearchPage completeSinglePage(NoonSearchPage page) {
        if (page != null) {
            page.setCoverageComplete(true);
        }
        return page;
    }

    private static void requireFirstPageCoverageMetadata(NoonSearchPage page) {
        if (page == null) {
            throw coverageFailure("Noon 前台搜索第一页未返回。", null);
        }
        if (page.getTotalHits() == null || page.getTotalPages() == null) {
            throw coverageFailure(
                    "Noon 前台搜索缺少总结果数或总页数，不能确认前 200 覆盖。",
                    page.getSourceUrl()
            );
        }
        if (page.getProviderPage() == null || page.getProviderPage() != 1) {
            throw coverageFailure(
                    "Noon 前台搜索第一页页码不匹配，不能写入排名。",
                    page.getSourceUrl()
            );
        }
        if (page.getProviderLimit() == null
                || page.getProviderLimit() != PROVIDER_PAGE_LIMIT) {
            throw coverageFailure(
                    "Noon 前台搜索第一页未确认按每页 100 条返回，不能写入前 200 排名。",
                    page.getSourceUrl()
            );
        }
        if (page.getTotalHits() < 0
                || page.getTotalPages() < 0
                || (page.getTotalHits() > 0 && page.getTotalPages() < 1)) {
            throw coverageFailure(
                    "Noon 前台搜索总结果数或总页数无效，不能写入排名。",
                    page.getSourceUrl()
            );
        }
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
        if (second.getProviderPage() == null || second.getProviderPage() != 2) {
            throw coverageFailure(
                    "Noon 前台搜索第二页页码无法确认，不能写入前 200 排名。",
                    second.getSourceUrl()
            );
        }
        if (second.getProviderLimit() == null
                || second.getProviderLimit() != PROVIDER_PAGE_LIMIT) {
            throw coverageFailure(
                    "Noon 前台搜索第二页未确认按每页 100 条返回，不能写入前 200 排名。",
                    second.getSourceUrl()
            );
        }
        /*
         * Noon 的列表总量会在两次独立请求之间变化，第二页也可能省略
         * nbHits/nbPages。总量逐字相等并不能证明排名连续，完整性应由
         * 已验证的第一页总量、页码/页容量和合并后的商品编码去重数保证。
         */
        if (first.getTotalHits() != null
                && first.getTotalHits() > PROVIDER_PAGE_LIMIT
                && (second.getResults() == null
                || second.getResults().isEmpty())) {
            throw coverageFailure(
                    "Noon 前台搜索声明存在第二页，但第二页没有商品结果。",
                    second.getSourceUrl()
            );
        }
    }

    private static void add(
            NoonSearchResultAccumulator accumulator,
            List<NoonSearchResult> results
    ) {
        if (results == null) {
            return;
        }
        for (NoonSearchResult result : results) {
            accumulator.add(result);
        }
    }

    private static int uniqueProductCount(List<NoonSearchResult> results) {
        Set<String> productCodes = new HashSet<>();
        if (results == null) {
            return 0;
        }
        for (NoonSearchResult result : results) {
            String code = NoonProductCodeSupport.normalize(
                    result == null ? null : result.getNoonProductCode()
            );
            if (code != null) {
                productCodes.add(code);
            }
        }
        return productCodes.size();
    }

    private static void requireCoveredResultCount(
            int actual,
            int expected,
            String sourceUrl
    ) {
        if (actual < expected) {
            throw coverageFailure(
                    "Noon 前台搜索分页结果不足：应覆盖 "
                            + expected
                            + " 条，实际仅解析 "
                            + actual
                            + " 条。",
                    sourceUrl
            );
        }
    }

    private static String joinSourceUrls(
            NoonSearchPage first,
            NoonSearchPage second
    ) {
        if (first != null && first.getSourceUrl() != null) {
            return first.getSourceUrl();
        }
        return second == null ? null : second.getSourceUrl();
    }

    private static LocalDateTime latestCapturedAt(
            NoonSearchPage first,
            NoonSearchPage second
    ) {
        LocalDateTime firstValue = first == null ? null : first.getCapturedAt();
        LocalDateTime secondValue = second == null ? null : second.getCapturedAt();
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
        String value = (first == null ? "" : String.valueOf(first.getResponseHash()))
                + "|"
                + (second == null ? "" : String.valueOf(second.getResponseHash()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                output.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return output.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Noon 搜索分页响应 hash 计算失败。", exception);
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
