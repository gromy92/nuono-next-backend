package com.nuono.next.aiopsdashboard;

public class AiOperationsDashboardQuery {

    private final Long ownerUserId;
    private final Long operatorUserId;
    private final String storeCode;
    private final String siteCode;
    private final String datePreset;

    public AiOperationsDashboardQuery(
            Long ownerUserId,
            Long operatorUserId,
            String storeCode,
            String siteCode,
            String datePreset
    ) {
        this.ownerUserId = ownerUserId;
        this.operatorUserId = operatorUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.datePreset = datePreset;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getDatePreset() {
        return datePreset;
    }
}
