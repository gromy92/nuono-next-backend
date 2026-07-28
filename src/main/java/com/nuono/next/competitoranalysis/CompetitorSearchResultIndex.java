package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class CompetitorSearchResultIndex {
    static final String ORGANIC = "ORGANIC";
    static final String SPONSORED = "SPONSORED";

    private final List<NoonSearchResult> orderedResults = new ArrayList<>();
    private final Map<String, NoonSearchResult> firstResultsByRankKey = new LinkedHashMap<>();

    static CompetitorSearchResultIndex from(List<NoonSearchResult> results, int rankScanDepth) {
        CompetitorSearchResultIndex index = new CompetitorSearchResultIndex();
        if (results == null) {
            return index;
        }
        for (NoonSearchResult result : results) {
            String code = result == null ? null : normalizeCode(result.getNoonProductCode());
            if (!StringUtils.hasText(code) || rankPosition(result) > rankScanDepth) {
                continue;
            }
            result.setNoonProductCode(code);
            result.setCodeType(NoonProductCodeSupport.codeType(code).orElse(result.getCodeType()));
            index.orderedResults.add(result);
            index.firstResultsByRankKey.putIfAbsent(rankKey(result), result);
        }
        return index;
    }

    List<NoonSearchResult> orderedResults() {
        return orderedResults;
    }

    Map<String, NoonSearchResult> firstResultsByCode(int uniqueProductLimit) {
        Map<String, NoonSearchResult> resultsByCode = new LinkedHashMap<>();
        for (NoonSearchResult result : orderedResults) {
            resultsByCode.putIfAbsent(result.getNoonProductCode(), result);
            if (resultsByCode.size() >= uniqueProductLimit) {
                break;
            }
        }
        return resultsByCode;
    }

    NoonSearchResult firstResult(String code, String rankChannel) {
        return firstResultsByRankKey.get(rankKey(code, rankChannel));
    }

    static int rankPosition(NoonSearchResult result) {
        Integer rankPosition = result == null ? null : result.getRankPosition();
        Integer rawPosition = result == null ? null : result.getPosition();
        int value = rankPosition == null ? (rawPosition == null ? Integer.MAX_VALUE : rawPosition) : rankPosition;
        return value < 1 ? Integer.MAX_VALUE : value;
    }

    static String rankKey(NoonSearchResult result) {
        return rankKey(result.getNoonProductCode(), rankChannel(result));
    }

    static String rankKey(String code, String rankChannel) {
        return normalizeCode(code) + "|" + normalizeRankChannel(rankChannel);
    }

    static String rankChannel(NoonSearchResult result) {
        return result != null && result.isSponsored() ? SPONSORED : ORGANIC;
    }

    private static String normalizeRankChannel(String value) {
        return SPONSORED.equalsIgnoreCase(value) ? SPONSORED : ORGANIC;
    }

    private static String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
