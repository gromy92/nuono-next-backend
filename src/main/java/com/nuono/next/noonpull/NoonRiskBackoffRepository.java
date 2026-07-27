package com.nuono.next.noonpull;

import java.time.LocalDateTime;

public interface NoonRiskBackoffRepository {
    void upsert(NoonRiskBackoffHold hold);

    NoonRiskBackoffHold selectActiveHold(String scopeKey, LocalDateTime now);

    default NoonRiskBackoffHold selectActiveAccountWideHold(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String operationGroup,
            LocalDateTime now
    ) {
        return null;
    }

    NoonRiskBackoffHold selectLatestHold(String scopeKey);

    default int resetAfterSuccess(String scopeKey, String sourceDomain, LocalDateTime resetAt) {
        return 0;
    }
}
