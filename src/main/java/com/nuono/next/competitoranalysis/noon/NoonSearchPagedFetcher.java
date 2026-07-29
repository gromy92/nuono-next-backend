package com.nuono.next.competitoranalysis.noon;

import java.util.function.Function;

final class NoonSearchPagedFetcher {
    private NoonSearchPagedFetcher() {
    }

    static NoonSearchPage search(
            NoonSearchRequest request,
            Function<NoonSearchRequest, NoonSearchPage> fetchPage
    ) {
        int requestedLimit =
                NoonSearchPaginationSupport.requestedLimit(request);
        NoonSearchPage first = fetchPage.apply(
                NoonSearchPaginationSupport.pageRequest(
                        request,
                        1,
                        Math.min(
                                requestedLimit,
                                NoonSearchPaginationSupport.PROVIDER_PAGE_LIMIT
                        )
                )
        );
        if (!NoonSearchPaginationSupport.needsSecondPage(
                first,
                requestedLimit
        )) {
            return NoonSearchPaginationSupport.completeSinglePage(first);
        }
        NoonSearchPage second = fetchPage.apply(
                NoonSearchPaginationSupport.pageRequest(
                        request,
                        2,
                        requestedLimit
                                - NoonSearchPaginationSupport.PROVIDER_PAGE_LIMIT
                )
        );
        return NoonSearchPaginationSupport.merge(
                first,
                second,
                requestedLimit
        );
    }
}
