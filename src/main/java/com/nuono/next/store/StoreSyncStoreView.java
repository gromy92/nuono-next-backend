package com.nuono.next.store;

import java.util.ArrayList;
import java.util.List;

public class StoreSyncStoreView {

    private Long id;

    private String projectName;

    private String projectCode;

    private String storeCode;

    private Boolean isAuthorized;

    private String noonUser;

    private String noonPartnerId;

    private String connectionStatus;

    private Integer siteCount;

    private Integer connectedSiteCount;

    private List<StoreSyncSiteView> siteStores = new ArrayList<>();

    private List<StoreSyncManagerInfo> managers = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
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

    public Boolean getIsAuthorized() {
        return isAuthorized;
    }

    public void setIsAuthorized(Boolean authorized) {
        isAuthorized = authorized;
    }

    public String getNoonUser() {
        return noonUser;
    }

    public void setNoonUser(String noonUser) {
        this.noonUser = noonUser;
    }

    public String getNoonPartnerId() {
        return noonPartnerId;
    }

    public void setNoonPartnerId(String noonPartnerId) {
        this.noonPartnerId = noonPartnerId;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    public Integer getSiteCount() {
        return siteCount;
    }

    public void setSiteCount(Integer siteCount) {
        this.siteCount = siteCount;
    }

    public Integer getConnectedSiteCount() {
        return connectedSiteCount;
    }

    public void setConnectedSiteCount(Integer connectedSiteCount) {
        this.connectedSiteCount = connectedSiteCount;
    }

    public List<StoreSyncSiteView> getSiteStores() {
        return siteStores;
    }

    public void setSiteStores(List<StoreSyncSiteView> siteStores) {
        this.siteStores = siteStores;
    }

    public List<StoreSyncManagerInfo> getManagers() {
        return managers;
    }

    public void setManagers(List<StoreSyncManagerInfo> managers) {
        this.managers = managers;
    }
}
