package com.nuono.next.operationsconfig;

public class OperationConfigStoreScope {

    private final Long ownerUserId;
    private final Long logicalStoreId;
    private final String projectCode;
    private final String projectName;
    private final String storeCode;
    private final String siteCode;

    public OperationConfigStoreScope(
            Long ownerUserId,
            Long logicalStoreId,
            String projectCode,
            String projectName,
            String storeCode,
            String siteCode
    ) {
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.projectCode = projectCode;
        this.projectName = projectName;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }
}
