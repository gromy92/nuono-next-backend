package com.nuono.next.operationsconfig;

public class OperationConfigVersionPublishRequest {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String message;

    public OperationConfigVersionPublishRequest() {
    }

    public OperationConfigVersionPublishRequest(Long ownerUserId, String storeCode, String siteCode, String message) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.message = message;
    }

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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
