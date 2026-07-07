package com.nuono.next.postsaleprofit;

public class PostSaleProfitBatchLockRequest {
    private String storeCode;
    private String siteCode;
    private Long batchId;
    private Boolean locked;
    private String reason;

    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Boolean getLocked() { return locked; }
    public void setLocked(Boolean locked) { this.locked = locked; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
