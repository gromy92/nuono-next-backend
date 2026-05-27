package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationCalendarRuleCopyPreviousYearRequest {

    private List<Long> bossUserIds;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private int sourceYear;
    private int targetYear;

    public OperationCalendarRuleCopyPreviousYearCommand toCommand() {
        return new OperationCalendarRuleCopyPreviousYearCommand(
                bossUserIds,
                ownerUserId,
                storeCode,
                siteCode,
                sourceYear,
                targetYear
        );
    }

    public List<Long> getBossUserIds() { return bossUserIds; }
    public void setBossUserIds(List<Long> bossUserIds) { this.bossUserIds = bossUserIds; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public int getSourceYear() { return sourceYear; }
    public void setSourceYear(int sourceYear) { this.sourceYear = sourceYear; }
    public int getTargetYear() { return targetYear; }
    public void setTargetYear(int targetYear) { this.targetYear = targetYear; }
}
