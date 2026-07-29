package com.nuono.next.competitoranalysis.noon;

import java.util.HashSet;
import java.util.List;
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
        return NoonSearchPaginationMergeSupport.merge(
                first,
                second,
                requestedLimit
        );
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
