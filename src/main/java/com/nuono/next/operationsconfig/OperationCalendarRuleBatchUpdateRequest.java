package com.nuono.next.operationsconfig;

import java.math.BigDecimal;
import java.util.List;

public class OperationCalendarRuleBatchUpdateRequest {

    private List<Long> bossUserIds;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private List<Long> ruleIds;
    private Boolean enabled;
    private BigDecimal factorValue;

    public OperationCalendarRuleBatchUpdateCommand toCommand() {
        return new OperationCalendarRuleBatchUpdateCommand(
                bossUserIds,
                ownerUserId,
                storeCode,
                siteCode,
                ruleIds,
                enabled,
                factorValue
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
    public List<Long> getRuleIds() { return ruleIds; }
    public void setRuleIds(List<Long> ruleIds) { this.ruleIds = ruleIds; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public BigDecimal getFactorValue() { return factorValue; }
    public void setFactorValue(BigDecimal factorValue) { this.factorValue = factorValue; }
}
