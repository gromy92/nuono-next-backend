package com.nuono.next.noonpull;

public class NoonPullStoreBinding {
    private final Long ownerUserId;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final String partnerId;
    private final String noonUser;
    private final String sessionProjectUser;
    private final String persistedCookie;

    public NoonPullStoreBinding(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            String partnerId,
            String noonUser,
            String persistedCookie
    ) {
        this(
                ownerUserId,
                projectCode,
                storeCode,
                siteCode,
                partnerId,
                noonUser,
                noonUser,
                persistedCookie
        );
    }

    public NoonPullStoreBinding(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            String partnerId,
            String noonUser,
            String sessionProjectUser,
            String persistedCookie
    ) {
        this.ownerUserId = ownerUserId;
        this.projectCode = projectCode;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerId = partnerId;
        this.noonUser = noonUser;
        this.sessionProjectUser = sessionProjectUser;
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

    public String getSessionProjectUser() {
        return sessionProjectUser;
    }

    public String getPersistedCookie() {
        return persistedCookie;
    }
}
