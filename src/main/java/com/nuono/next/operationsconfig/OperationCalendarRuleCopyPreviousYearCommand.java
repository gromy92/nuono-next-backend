package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationCalendarRuleCopyPreviousYearCommand {

    private final List<Long> bossUserIds;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final int sourceYear;
    private final int targetYear;

    public OperationCalendarRuleCopyPreviousYearCommand(
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            int sourceYear,
            int targetYear
    ) {
        this.bossUserIds = bossUserIds;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.sourceYear = sourceYear;
        this.targetYear = targetYear;
    }

    public List<Long> getBossUserIds() { return bossUserIds; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public int getSourceYear() { return sourceYear; }
    public int getTargetYear() { return targetYear; }
}
