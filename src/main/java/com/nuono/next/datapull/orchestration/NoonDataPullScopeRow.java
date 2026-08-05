package com.nuono.next.datapull.orchestration;

/** Read model for one active, bound Noon store/site scope. */
public class NoonDataPullScopeRow {

    private Long ownerUserId;
    private Long logicalStoreId;
    private Long logicalStoreSiteId;
    private Long userProjectId;
    private Long userStoreId;
    private String projectCode;
    private String storeCode;
    private String siteCode;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public void setLogicalStoreId(Long logicalStoreId) {
        this.logicalStoreId = logicalStoreId;
    }

    public Long getLogicalStoreSiteId() {
        return logicalStoreSiteId;
    }

    public void setLogicalStoreSiteId(Long logicalStoreSiteId) {
        this.logicalStoreSiteId = logicalStoreSiteId;
    }

    public Long getUserProjectId() {
        return userProjectId;
    }

    public void setUserProjectId(Long userProjectId) {
        this.userProjectId = userProjectId;
    }

    public Long getUserStoreId() {
        return userStoreId;
    }

    public void setUserStoreId(Long userStoreId) {
        this.userStoreId = userStoreId;
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
