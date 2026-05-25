package com.nuono.next.noonpull;

import org.springframework.stereotype.Service;

@Service
public class NoonBusinessReadinessConsumers {
    private final NoonBusinessReadinessService readinessService;

    public NoonBusinessReadinessConsumers(NoonBusinessReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    public NoonBusinessReadinessView productManagement(Long ownerUserId, String storeCode, String siteCode) {
        return readinessService.readiness(ownerUserId, storeCode, siteCode, NoonPullDataDomain.PRODUCT);
    }

    public NoonBusinessReadinessView salesAnalysis(Long ownerUserId, String storeCode, String siteCode) {
        return readinessService.readiness(ownerUserId, storeCode, siteCode, NoonPullDataDomain.SALES);
    }

    public NoonBusinessReadinessView salesForecast(Long ownerUserId, String storeCode, String siteCode) {
        return readinessService.readiness(ownerUserId, storeCode, siteCode, NoonPullDataDomain.SALES);
    }

    public NoonBusinessReadinessView aiDashboardSales(Long ownerUserId, String storeCode, String siteCode) {
        return readinessService.readiness(ownerUserId, storeCode, siteCode, NoonPullDataDomain.SALES);
    }

    public NoonBusinessReadinessView orderReadiness(Long ownerUserId, String storeCode, String siteCode) {
        return readinessService.readiness(ownerUserId, storeCode, siteCode, NoonPullDataDomain.ORDER);
    }
}
