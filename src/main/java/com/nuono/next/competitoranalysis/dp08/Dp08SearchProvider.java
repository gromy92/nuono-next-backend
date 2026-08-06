package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.runtime.ProviderOutcome;

/** DP-08 provider seam; every method performs exactly one frontend request. */
public interface Dp08SearchProvider {
    ProviderOutcome<NoonSearchPage> fetchRankPage(Dp08KeywordScope scope, int pageNo);

    ProviderOutcome<NoonSearchPage> searchExact(Dp08ListTarget target, String locale);

    default ProviderOutcome<NoonSearchPage> fetchRankPage(
            Dp08MemberSetHandle handle,int pageNo
    ) {
        return fetchRankPage(handle.keywordProviderScope(),pageNo);
    }

    default ProviderOutcome<NoonSearchPage> searchExact(
            Dp08MemberSetHandle handle,java.time.LocalDate factDate,String locale
    ) {
        return searchExact(handle.listProviderTarget(factDate,true),locale);
    }
}
