package com.nuono.next.noonpull;

import java.util.Locale;
import org.springframework.util.StringUtils;

public class NoonRiskBackoffScope {
    private final String scopeType;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String operationGroup;
    private final String scopeKey;

    private NoonRiskBackoffScope(
            String scopeType,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String operationGroup
    ) {
        this.scopeType = normalize(scopeType);
        this.ownerUserId = ownerUserId;
        this.storeCode = normalize(storeCode);
        this.siteCode = normalize(siteCode);
        this.operationGroup = normalize(operationGroup);
        this.scopeKey = buildScopeKey();
    }

    public static NoonRiskBackoffScope report(Long ownerUserId, String storeCode, String siteCode) {
        return new NoonRiskBackoffScope("OWNER_STORE_SITE", ownerUserId, storeCode, siteCode, "REPORT");
    }

    public static NoonRiskBackoffScope report(NoonReportPullRequest request) {
        if (request == null) {
            return report(null, null, null);
        }
        return report(request.getOwnerUserId(), request.getStoreCode(), request.getSiteCode());
    }

    public static NoonRiskBackoffScope productInterface(Long ownerUserId, String storeCode, String siteCode) {
        return new NoonRiskBackoffScope("OWNER_STORE_SITE", ownerUserId, storeCode, siteCode, "PRODUCT");
    }

    public static NoonRiskBackoffScope productInterface(NoonInterfacePullRequest request) {
        if (request == null) {
            return productInterface(null, null, null);
        }
        return productInterface(request.getOwnerUserId(), request.getStoreCode(), request.getSiteCode());
    }

    public static NoonRiskBackoffScope interfacePull(NoonInterfacePullRequest request) {
        if (request == null) {
            return allNoon(null, null, null);
        }
        String operationGroup = request.getDataDomain() == null ? "INTERFACE" : request.getDataDomain().name();
        return new NoonRiskBackoffScope(
                "OWNER_STORE_SITE",
                request.getOwnerUserId(),
                request.getStoreCode(),
                request.getSiteCode(),
                operationGroup
        );
    }

    public static NoonRiskBackoffScope publicDetail(Long ownerUserId, String storeCode, String siteCode) {
        return new NoonRiskBackoffScope("OWNER_STORE_SITE", ownerUserId, storeCode, siteCode, "PUBLIC_DETAIL");
    }

    public static NoonRiskBackoffScope publicSearch(Long ownerUserId, String storeCode, String siteCode) {
        return new NoonRiskBackoffScope("OWNER_STORE_SITE", ownerUserId, storeCode, siteCode, "PUBLIC_SEARCH");
    }

    public static NoonRiskBackoffScope sourceCollection(Long ownerUserId, String storeCode, String siteCode) {
        return new NoonRiskBackoffScope("OWNER_STORE_SITE", ownerUserId, storeCode, siteCode, "SOURCE_COLLECTION");
    }

    public static NoonRiskBackoffScope allNoon(Long ownerUserId, String storeCode, String siteCode) {
        return new NoonRiskBackoffScope("OWNER_STORE_SITE", ownerUserId, storeCode, siteCode, "NOON");
    }

    NoonRiskBackoffScope accountWide() {
        return allNoon(ownerUserId, storeCode, siteCode);
    }

    public String getScopeType() {
        return scopeType;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getOperationGroup() {
        return operationGroup;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    private String buildScopeKey() {
        return "type:" + value(scopeType)
                + "|owner:" + value(ownerUserId)
                + "|store:" + value(storeCode)
                + "|site:" + value(siteCode)
                + "|operation:" + value(operationGroup);
    }

    private static String value(Object value) {
        return value == null ? "*" : String.valueOf(value);
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
