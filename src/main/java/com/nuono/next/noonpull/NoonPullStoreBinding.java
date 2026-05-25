package com.nuono.next.noonpull;

public class NoonPullStoreBinding {
    private final Long ownerUserId;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final String partnerId;
    private final String noonUser;
    private final String noonPassword;
    private final String persistedCookie;

    public NoonPullStoreBinding(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            String partnerId,
            String noonUser,
            String noonPassword,
            String persistedCookie
    ) {
        this.ownerUserId = ownerUserId;
        this.projectCode = projectCode;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerId = partnerId;
        this.noonUser = noonUser;
        this.noonPassword = noonPassword;
        this.persistedCookie = persistedCookie;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public String getNoonUser() {
        return noonUser;
    }

    public String getNoonPassword() {
        return noonPassword;
    }

    public String getPersistedCookie() {
        return persistedCookie;
    }
}
