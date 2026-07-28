package com.nuono.next.competitoranalysis;

import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

final class CompetitorTestRiskBackoffRepository implements NoonRiskBackoffRepository {
    private final Map<String, NoonRiskBackoffHold> holds = new LinkedHashMap<>();

    @Override
    public void upsert(NoonRiskBackoffHold hold) {
        holds.put(hold.getScopeKey(), hold.copy());
    }

    @Override
    public NoonRiskBackoffHold selectActiveHold(String scopeKey, LocalDateTime now) {
        NoonRiskBackoffHold hold = holds.get(scopeKey);
        if (hold == null || hold.getBlockedUntil() == null || !hold.getBlockedUntil().isAfter(now)) {
            return null;
        }
        return hold.copy();
    }

    @Override
    public NoonRiskBackoffHold selectLatestHold(String scopeKey) {
        NoonRiskBackoffHold hold = holds.get(scopeKey);
        return hold == null ? null : hold.copy();
    }
}
