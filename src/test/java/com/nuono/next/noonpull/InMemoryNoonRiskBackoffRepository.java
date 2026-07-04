package com.nuono.next.noonpull;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InMemoryNoonRiskBackoffRepository implements NoonRiskBackoffRepository {
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
    public NoonRiskBackoffHold selectActiveAccountWideHold(Long ownerUserId, String storeCode, String siteCode, LocalDateTime now) {
        return holds.values().stream()
                .filter(hold -> "NOON".equals(hold.getOperationGroup()))
                .filter(hold -> same(hold.getOwnerUserId(), ownerUserId))
                .filter(hold -> same(hold.getStoreCode(), storeCode))
                .filter(hold -> siteCode == null || same(hold.getSiteCode(), siteCode) || hold.getSiteCode() == null)
                .filter(hold -> hold.getBlockedUntil() != null && hold.getBlockedUntil().isAfter(now))
                .max((first, second) -> first.getBlockedUntil().compareTo(second.getBlockedUntil()))
                .map(NoonRiskBackoffHold::copy)
                .orElse(null);
    }

    @Override
    public NoonRiskBackoffHold selectLatestHold(String scopeKey) {
        NoonRiskBackoffHold hold = holds.get(scopeKey);
        return hold == null ? null : hold.copy();
    }

    private boolean same(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
