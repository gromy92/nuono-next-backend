package com.nuono.next.noonpull;

public class NoonProductDetailBaselineSyncRequest {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private int maxDetailFetches;
    private String resumePosition;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public int getMaxDetailFetches() {
        return maxDetailFetches;
    }

    public void setMaxDetailFetches(int maxDetailFetches) {
        this.maxDetailFetches = Math.max(maxDetailFetches, 0);
    }

    public String getResumePosition() {
        return resumePosition;
    }

    public void setResumePosition(String resumePosition) {
        this.resumePosition = resumePosition;
    }
}
