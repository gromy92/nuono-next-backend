package com.nuono.next.noonpull;

import java.util.List;

public class NoonProductPullPlanPolicy {

    public List<NoonPullPlanDraft> defaultProductPlans(Long ownerUserId, String storeCode, String siteCode) {
        return List.of(
                NoonPullPlanDraft.builder()
                        .ownerUserId(ownerUserId)
                        .storeCode(storeCode)
                        .siteCode(siteCode)
                        .pullType(NoonPullType.INTERFACE)
                        .dataDomain(NoonPullDataDomain.PRODUCT)
                        .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                        .scheduleExpression("manual")
                        .build(),
                NoonPullPlanDraft.builder()
                        .ownerUserId(ownerUserId)
                        .storeCode(storeCode)
                        .siteCode(siteCode)
                        .pullType(NoonPullType.INTERFACE)
                        .dataDomain(NoonPullDataDomain.PRODUCT)
                        .triggerMode(NoonPullTriggerMode.READBACK_CHECK)
                        .scheduleExpression("on-demand")
                        .build()
        );
    }
}
