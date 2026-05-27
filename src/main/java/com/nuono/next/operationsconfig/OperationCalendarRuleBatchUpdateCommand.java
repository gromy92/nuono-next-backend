package com.nuono.next.operationsconfig;

import java.math.BigDecimal;
import java.util.List;

public class OperationCalendarRuleBatchUpdateCommand {

    private final List<Long> bossUserIds;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final List<Long> ruleIds;
    private final Boolean enabled;
    private final BigDecimal factorValue;

    public OperationCalendarRuleBatchUpdateCommand(
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            List<Long> ruleIds,
            Boolean enabled,
            BigDecimal factorValue
    ) {
        this.bossUserIds = bossUserIds;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.ruleIds = ruleIds;
        this.enabled = enabled;
        this.factorValue = factorValue;
    }

    public List<Long> getBossUserIds() { return bossUserIds; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public List<Long> getRuleIds() { return ruleIds; }
    public Boolean getEnabled() { return enabled; }
    public BigDecimal getFactorValue() { return factorValue; }
}
