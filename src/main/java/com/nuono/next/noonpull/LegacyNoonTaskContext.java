package com.nuono.next.noonpull;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.Map;
import java.util.Set;

/** Builds the access and request context shared by official-warehouse executors. */
final class LegacyNoonTaskContext {
    private LegacyNoonTaskContext() {
    }

    static BusinessAccessContext businessAccess(NoonPullTaskRecord task) {
        return BusinessAccessContext.builder()
                .sessionUserId(task.getOwnerUserId())
                .businessOwnerUserId(task.getOwnerUserId())
                .storeCodes(Set.of(task.getStoreCode()))
                .storeOwnerUserIds(Map.of(task.getStoreCode(), task.getOwnerUserId()))
                .build();
    }

    static NoonInterfacePullRequest warehouseRequest(
            NoonPullTaskRecord task,
            String requestName
    ) {
        return NoonInterfacePullRequest.builder()
                .ownerUserId(task.getOwnerUserId())
                .storeCode(task.getStoreCode())
                .siteCode(task.getSiteCode())
                .dataDomain(task.getDataDomain())
                .requestName(requestName)
                .targetIdentity(task.getTargetIdentity())
                .dateFrom(task.getTargetDateFrom())
                .dateTo(task.getTargetDateTo())
                .requestSummary("scheduled daily official warehouse pull")
                .build();
    }
}
