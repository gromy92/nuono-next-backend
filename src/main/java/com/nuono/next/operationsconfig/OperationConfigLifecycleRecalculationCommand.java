package com.nuono.next.operationsconfig;

import java.time.LocalDate;
import java.util.List;

public class OperationConfigLifecycleRecalculationCommand {

    private final List<Long> bossUserIds;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate anchorDate;
    private final String selectedRuleVersion;

    public OperationConfigLifecycleRecalculationCommand(
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate anchorDate,
            String selectedRuleVersion
    ) {
        this.bossUserIds = bossUserIds == null ? List.of() : List.copyOf(bossUserIds);
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.anchorDate = anchorDate;
        this.selectedRuleVersion = selectedRuleVersion;
    }

    public List<Long> getBossUserIds() { return bossUserIds; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public String getSelectedRuleVersion() { return selectedRuleVersion; }
}
