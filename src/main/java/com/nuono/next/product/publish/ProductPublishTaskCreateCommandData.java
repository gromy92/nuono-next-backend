package com.nuono.next.product.publish;

/** Data-only base for the publish-task create command. */
public class ProductPublishTaskCreateCommandData {
    private Long ownerUserId;
    private Long productMasterId;
    private String storeCode;
    private String projectCode;
    private String skuParent;
    private String partnerSku;
    private String pskuCode;
    private String currentSiteCode;
    private String draftJson;
    private String baselineJson;
    private String draftHash;
    private String changedDomainsJson;
    private String requestJson;
    private String idempotencyKey;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long value) { this.ownerUserId = value; }
    public Long getProductMasterId() { return productMasterId; }
    public void setProductMasterId(Long value) { this.productMasterId = value; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String value) { this.storeCode = value; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String value) { this.projectCode = value; }
    public String getSkuParent() { return skuParent; }
    public void setSkuParent(String value) { this.skuParent = value; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String value) { this.partnerSku = value; }
    public String getPskuCode() { return pskuCode; }
    public void setPskuCode(String value) { this.pskuCode = value; }
    public String getCurrentSiteCode() { return currentSiteCode; }
    public void setCurrentSiteCode(String value) { this.currentSiteCode = value; }
    public String getDraftJson() { return draftJson; }
    public void setDraftJson(String value) { this.draftJson = value; }
    public String getBaselineJson() { return baselineJson; }
    public void setBaselineJson(String value) { this.baselineJson = value; }
    public String getDraftHash() { return draftHash; }
    public void setDraftHash(String value) { this.draftHash = value; }
    public String getChangedDomainsJson() { return changedDomainsJson; }
    public void setChangedDomainsJson(String value) { this.changedDomainsJson = value; }
    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String value) { this.requestJson = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
}
