package com.nuono.next.noonauth;

public class NoonAuthRecoveryProjectCandidate {
    private Long ownerUserId;
    private String projectCode;
    private String storeCode;

    public NoonAuthRecoveryProjectCandidate() {
    }

    public NoonAuthRecoveryProjectCandidate(Long ownerUserId, String projectCode, String storeCode) {
        this.ownerUserId = ownerUserId;
        this.projectCode = projectCode;
        this.storeCode = storeCode;
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
}
