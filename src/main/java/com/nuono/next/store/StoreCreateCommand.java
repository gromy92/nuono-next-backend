package com.nuono.next.store;

public class StoreCreateCommand {

    private Long ownerUserId;

    private String projectName;

    private String storeCode;

    private String site;

    private String projectCode;

    private String orgCode;

    private String orgName;

    private String noonUser;

    private String noonProjectUser;

    private String noonPassword;

    private String noonPartnerId;

    private String noonEmailAuthCode;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getNoonUser() {
        return noonUser;
    }

    public void setNoonUser(String noonUser) {
        this.noonUser = noonUser;
    }

    public String getNoonProjectUser() {
        return noonProjectUser;
    }

    public void setNoonProjectUser(String noonProjectUser) {
        this.noonProjectUser = noonProjectUser;
    }

    public String getNoonPassword() {
        return noonPassword;
    }

    public void setNoonPassword(String noonPassword) {
        this.noonPassword = noonPassword;
    }

    public String getNoonEmailAuthCode() {
        return noonEmailAuthCode;
    }

    public void setNoonEmailAuthCode(String noonEmailAuthCode) {
        this.noonEmailAuthCode = noonEmailAuthCode;
    }

    public String getNoonPartnerId() {
        return noonPartnerId;
    }

    public void setNoonPartnerId(String noonPartnerId) {
        this.noonPartnerId = noonPartnerId;
    }
}
