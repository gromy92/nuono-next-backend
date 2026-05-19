package com.nuono.next.store;

import java.time.LocalDateTime;

public class StoreSyncStoreRecord {

    private Long id;

    private String projectName;

    private String storeCode;

    private String site;

    private Boolean ownerAuthorized;

    private String projectCode;

    private String noonPartnerUser;

    private String noonPartnerProjectUser;

    private String noonPartnerPwd;

    private String noonPartnerCookie;

    private LocalDateTime cookieGenerateTime;

    private String noonPartnerId;

    private Integer bindStatus;

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

    public Boolean getOwnerAuthorized() {
        return ownerAuthorized;
    }

    public void setOwnerAuthorized(Boolean ownerAuthorized) {
        this.ownerAuthorized = ownerAuthorized;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getNoonPartnerUser() {
        return noonPartnerUser;
    }

    public void setNoonPartnerUser(String noonPartnerUser) {
        this.noonPartnerUser = noonPartnerUser;
    }

    public String getNoonPartnerProjectUser() {
        return noonPartnerProjectUser;
    }

    public void setNoonPartnerProjectUser(String noonPartnerProjectUser) {
        this.noonPartnerProjectUser = noonPartnerProjectUser;
    }

    public String getNoonPartnerPwd() {
        return noonPartnerPwd;
    }

    public void setNoonPartnerPwd(String noonPartnerPwd) {
        this.noonPartnerPwd = noonPartnerPwd;
    }

    public String getNoonPartnerCookie() {
        return noonPartnerCookie;
    }

    public void setNoonPartnerCookie(String noonPartnerCookie) {
        this.noonPartnerCookie = noonPartnerCookie;
    }

    public LocalDateTime getCookieGenerateTime() {
        return cookieGenerateTime;
    }

    public void setCookieGenerateTime(LocalDateTime cookieGenerateTime) {
        this.cookieGenerateTime = cookieGenerateTime;
    }

    public String getNoonPartnerId() {
        return noonPartnerId;
    }

    public void setNoonPartnerId(String noonPartnerId) {
        this.noonPartnerId = noonPartnerId;
    }

    public Integer getBindStatus() {
        return bindStatus;
    }

    public void setBindStatus(Integer bindStatus) {
        this.bindStatus = bindStatus;
    }
}
