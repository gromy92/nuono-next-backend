package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationLifecycleRuleCreateDraftCommand {

    private final List<Long> bossUserIds;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final Long bundleVersionId;

    public OperationLifecycleRuleCreateDraftCommand(
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        this(bossUserIds, ownerUserId, storeCode, siteCode, null);
    }

    public OperationLifecycleRuleCreateDraftCommand(
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long bundleVersionId
    ) {
        this.bossUserIds = bossUserIds == null ? List.of() : List.copyOf(bossUserIds);
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.bundleVersionId = bundleVersionId;
    }

    public List<Long> getBossUserIds() { return bossUserIds; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public Long getBundleVersionId() { return bundleVersionId; }
}
