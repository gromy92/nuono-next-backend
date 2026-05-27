package com.nuono.next.sales;

import java.time.LocalDate;

public class ProductLifecycleCalculationScope {

    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate anchorDate;
    private final String ruleVersion;
    private final boolean rerun;
    private final boolean explicitRuleVersion;
    private final Long triggeredByUserId;
    private final String triggerSource;

    public ProductLifecycleCalculationScope(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate anchorDate,
            String ruleVersion,
            boolean rerun
    ) {
        this(ownerUserId, storeCode, siteCode, anchorDate, ruleVersion, rerun, false, null, null);
    }

    public ProductLifecycleCalculationScope(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate anchorDate,
            String ruleVersion,
            boolean rerun,
            boolean explicitRuleVersion,
            Long triggeredByUserId,
            String triggerSource
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.anchorDate = anchorDate;
        this.ruleVersion = ruleVersion == null || ruleVersion.isBlank()
                ? ProductLifecycleResult.DEFAULT_RULE_VERSION
                : ruleVersion;
        this.rerun = rerun;
        this.explicitRuleVersion = explicitRuleVersion;
        this.triggeredByUserId = triggeredByUserId;
        this.triggerSource = triggerSource;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public LocalDate getAnchorDate() {
        return anchorDate;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public boolean isRerun() {
        return rerun;
    }

    public boolean isExplicitRuleVersion() {
        return explicitRuleVersion;
    }

    public Long getTriggeredByUserId() {
        return triggeredByUserId;
    }

    public String getTriggerSource() {
        return triggerSource;
    }
}
