package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationLifecycleRuleDraftCommand {

    private final Long id;
    private final List<Long> bossUserIds;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final OperationLifecycleRuleThresholds thresholds;
    private final Long bundleVersionId;

    public OperationLifecycleRuleDraftCommand(
            Long id,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            OperationLifecycleRuleThresholds thresholds
    ) {
        this(id, bossUserIds, ownerUserId, storeCode, siteCode, thresholds, null);
    }

    public OperationLifecycleRuleDraftCommand(
            Long id,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            OperationLifecycleRuleThresholds thresholds,
            Long bundleVersionId
    ) {
        this.id = id;
        this.bossUserIds = bossUserIds == null ? List.of() : List.copyOf(bossUserIds);
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.thresholds = thresholds;
        this.bundleVersionId = bundleVersionId;
    }

    public Long getId() { return id; }
    public List<Long> getBossUserIds() { return bossUserIds; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public OperationLifecycleRuleThresholds getThresholds() { return thresholds; }
    public Long getBundleVersionId() { return bundleVersionId; }
}
