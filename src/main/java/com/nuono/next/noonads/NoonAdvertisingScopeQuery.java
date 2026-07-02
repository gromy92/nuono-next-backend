package com.nuono.next.noonads;

import org.springframework.util.StringUtils;

public class NoonAdvertisingScopeQuery {
    private final Long ownerUserId;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;

    public NoonAdvertisingScopeQuery(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode
    ) {
        this.ownerUserId = ownerUserId;
        this.projectCode = normalize(projectCode);
        this.storeCode = normalize(storeCode);
        this.siteCode = normalize(siteCode);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getProjectCode() { return projectCode; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
}
