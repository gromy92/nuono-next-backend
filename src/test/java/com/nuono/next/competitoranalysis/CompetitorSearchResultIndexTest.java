package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompetitorSearchResultIndexTest {

    @Test
    void scanDepthUsesChannelRelativeRankWithoutDroppingRawPositionsAfterAds() {
        NoonSearchResult sponsored = result(1, 1, "NAD000001", true);
        NoonSearchResult lastOrganicInDepth = result(101, 100, "NKEEP0100", false);
        NoonSearchResult firstOrganicOutOfDepth = result(102, 101, "NDROP0101", false);

        CompetitorSearchResultIndex index = CompetitorSearchResultIndex.from(
                List.of(sponsored, lastOrganicInDepth, firstOrganicOutOfDepth),
                100
        );

        assertEquals(List.of(sponsored, lastOrganicInDepth), index.orderedResults());
        assertEquals(101, index.firstResult("NKEEP0100", "ORGANIC").getPosition());
    }

    @Test
    void candidatesRemainUniqueByProductAcrossRankChannels() {
        NoonSearchResult sponsored = result(1, 1, "NDUAL0001", true);
        NoonSearchResult organic = result(2, 1, "NDUAL0001", false);
        NoonSearchResult other = result(3, 2, "NOTHER002", false);
        CompetitorSearchResultIndex index =
                CompetitorSearchResultIndex.from(List.of(sponsored, organic, other), 100);

        assertEquals(List.of("NDUAL0001", "NOTHER002"), List.copyOf(index.firstResultsByCode(20).keySet()));
        assertEquals(sponsored, index.firstResult("NDUAL0001", "SPONSORED"));
        assertEquals(organic, index.firstResult("NDUAL0001", "ORGANIC"));
    }

    @Test
    void aSponsoredHitAtRawPosition21DoesNotDisplaceTheTwentiethCandidate() {
        List<NoonSearchResult> results = new ArrayList<>();
        for (int position = 1; position <= 20; position++) {
            results.add(result(position, position, String.format("N%08d", position), false));
        }
        NoonSearchResult laterSponsored = result(21, 1, "N00000021", true);
        results.add(laterSponsored);

        CompetitorSearchResultIndex index = CompetitorSearchResultIndex.from(results, 100);
        List<String> candidates = List.copyOf(index.firstResultsByCode(20).keySet());

        assertEquals("N00000020", candidates.get(19));
        assertEquals(false, candidates.contains(laterSponsored.getNoonProductCode()));
    }

    private static NoonSearchResult result(
            int rawPosition,
            int rankPosition,
            String code,
            boolean sponsored
    ) {
        NoonSearchResult result = new NoonSearchResult();
        result.setPosition(rawPosition);
        result.setRankPosition(rankPosition);
        result.setNoonProductCode(code);
        result.setSponsored(sponsored);
        return result;
    }
}
