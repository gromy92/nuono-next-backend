package com.nuono.next.operationsconfig;

import java.time.LocalDate;
import java.util.List;

public class OperationConfigLifecycleRecalculationRequest {

    private List<Long> bossUserIds;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private LocalDate anchorDate;
    private String selectedRuleVersion;

    public OperationConfigLifecycleRecalculationCommand toCommand() {
        return new OperationConfigLifecycleRecalculationCommand(
                bossUserIds,
                ownerUserId,
                storeCode,
                siteCode,
                anchorDate,
                selectedRuleVersion
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
    public LocalDate getAnchorDate() { return anchorDate; }
    public void setAnchorDate(LocalDate anchorDate) { this.anchorDate = anchorDate; }
    public String getSelectedRuleVersion() { return selectedRuleVersion; }
    public void setSelectedRuleVersion(String selectedRuleVersion) { this.selectedRuleVersion = selectedRuleVersion; }
}
