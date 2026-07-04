package com.nuono.next.sales;

public class NoonSalesReportBinding {

    private final Long ownerUserId;
    private final Long logicalStoreId;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final String partnerId;
    private final String noonUser;
    private final String noonPassword;
    private final String noonEmailAuthCode;
    private final String persistedCookie;

    public NoonSalesReportBinding(
            Long ownerUserId,
            Long logicalStoreId,
            String projectCode,
            String storeCode,
            String siteCode,
            String partnerId,
            String noonUser,
            String noonPassword,
            String noonEmailAuthCode,
            String persistedCookie
    ) {
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.projectCode = projectCode;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerId = partnerId;
        this.noonUser = noonUser;
        this.noonPassword = noonPassword;
        this.noonEmailAuthCode = noonEmailAuthCode;
        this.persistedCookie = persistedCookie;
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

    public String getNoonEmailAuthCode() {
        return noonEmailAuthCode;
    }

    public String getPersistedCookie() {
        return persistedCookie;
    }
}
