package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationLifecycleRuleDraftRequest {

    private Long id;
    private List<Long> bossUserIds;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private OperationLifecycleRuleThresholds thresholds;
    private Long bundleVersionId;

    public OperationLifecycleRuleDraftCommand toCommand() {
        return new OperationLifecycleRuleDraftCommand(id, bossUserIds, ownerUserId, storeCode, siteCode, thresholds, bundleVersionId);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public List<Long> getBossUserIds() { return bossUserIds; }
    public void setBossUserIds(List<Long> bossUserIds) { this.bossUserIds = bossUserIds; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public OperationLifecycleRuleThresholds getThresholds() { return thresholds; }
    public void setThresholds(OperationLifecycleRuleThresholds thresholds) { this.thresholds = thresholds; }
    public Long getBundleVersionId() { return bundleVersionId; }
    public void setBundleVersionId(Long bundleVersionId) { this.bundleVersionId = bundleVersionId; }
}
