package com.nuono.next.competitoranalysis.noon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class NoonSearchResultAccumulator {
    private final Map<String, NoonSearchResult> resultsByRankKey = new LinkedHashMap<>();
    private int resultPosition;
    private int organicRankPosition;
    private int sponsoredRankPosition;

    void add(NoonSearchResult result) {
        add(result, false);
    }

    void addScannedSlot(NoonSearchResult result) {
        add(result, true);
    }

    private void add(NoonSearchResult result, boolean consumeDuplicateSlot) {
        if (result == null || !StringUtils.hasText(result.getNoonProductCode())) {
            return;
        }
        String code = result.getNoonProductCode().trim().toUpperCase(Locale.ROOT);
        String channel = result.isSponsored() ? "SPONSORED" : "ORGANIC";
        String rankKey = code + "|" + channel;
        boolean duplicate = resultsByRankKey.containsKey(rankKey);
        if (duplicate && !consumeDuplicateSlot) {
            return;
        }
        result.setNoonProductCode(code);
        result.setPosition(++resultPosition);
        result.setRankPosition(result.isSponsored() ? ++sponsoredRankPosition : ++organicRankPosition);
        if (!duplicate) {
            resultsByRankKey.put(rankKey, result);
        }
    }

    boolean isEmpty() {
        return resultsByRankKey.isEmpty();
    }

    int size() {
        return resultsByRankKey.size();
    }

    List<NoonSearchResult> results() {
        return new ArrayList<>(resultsByRankKey.values());
    }
}
