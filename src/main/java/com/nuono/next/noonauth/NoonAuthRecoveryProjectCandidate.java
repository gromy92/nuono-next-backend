package com.nuono.next.noonauth;

public class NoonAuthRecoveryProjectCandidate {
    private Long ownerUserId;
    private String projectCode;
    private String storeCode;
    private String siteCode;

    public NoonAuthRecoveryProjectCandidate() {
    }

    public NoonAuthRecoveryProjectCandidate(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode
    ) {
        this.ownerUserId = ownerUserId;
        this.projectCode = projectCode;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
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
}
