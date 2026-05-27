package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationLifecycleRuleCreateDraftRequest {

    private List<Long> bossUserIds;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private Long bundleVersionId;

    public OperationLifecycleRuleCreateDraftCommand toCommand() {
        return new OperationLifecycleRuleCreateDraftCommand(bossUserIds, ownerUserId, storeCode, siteCode, bundleVersionId);
    }

    public List<Long> getBossUserIds() { return bossUserIds; }
    public void setBossUserIds(List<Long> bossUserIds) { this.bossUserIds = bossUserIds; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public Long getBundleVersionId() { return bundleVersionId; }
    public void setBundleVersionId(Long bundleVersionId) { this.bundleVersionId = bundleVersionId; }
}
