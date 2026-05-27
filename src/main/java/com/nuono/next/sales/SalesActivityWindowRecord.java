package com.nuono.next.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalesActivityWindowRecord {

    private final Long id;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String name;
    private final String activityType;
    private final String categoryScope;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final BigDecimal factor;
    private final boolean enabled;
    private final int versionNo;
    private final Long createdBy;
    private final Long updatedBy;
    private final Long operationsConfigBundleVersionId;
    private final String operationsConfigBundleVersionNo;
    private final String operationsConfigSourceRole;
    private final String operationsConfigSourceLabel;

    public SalesActivityWindowRecord(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String name,
            String activityType,
            String categoryScope,
            LocalDate dateFrom,
            LocalDate dateTo,
            BigDecimal factor,
            boolean enabled,
            int versionNo,
            Long createdBy,
            Long updatedBy
    ) {
        this(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                name,
                activityType,
                categoryScope,
                dateFrom,
                dateTo,
                factor,
                enabled,
                versionNo,
                createdBy,
                updatedBy,
                null,
                null,
                null,
                null
        );
    }

    public SalesActivityWindowRecord(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String name,
            String activityType,
            String categoryScope,
            LocalDate dateFrom,
            LocalDate dateTo,
            BigDecimal factor,
            boolean enabled,
            int versionNo,
            Long createdBy,
            Long updatedBy,
            Long operationsConfigBundleVersionId,
            String operationsConfigBundleVersionNo,
            String operationsConfigSourceRole,
            String operationsConfigSourceLabel
    ) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.name = name;
        this.activityType = activityType;
        this.categoryScope = categoryScope;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.factor = factor;
        this.enabled = enabled;
        this.versionNo = versionNo;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.operationsConfigBundleVersionId = operationsConfigBundleVersionId;
        this.operationsConfigBundleVersionNo = operationsConfigBundleVersionNo;
        this.operationsConfigSourceRole = operationsConfigSourceRole;
        this.operationsConfigSourceLabel = operationsConfigSourceLabel;
    }

    public SalesActivityWindowRecord withId(Long id) {
        return new SalesActivityWindowRecord(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                name,
                activityType,
                categoryScope,
                dateFrom,
                dateTo,
                factor,
                enabled,
                versionNo,
                createdBy,
                updatedBy,
                operationsConfigBundleVersionId,
                operationsConfigBundleVersionNo,
                operationsConfigSourceRole,
                operationsConfigSourceLabel
        );
    }

    public SalesActivityWindowRecord withEnabled(boolean enabled, Long updatedBy) {
        return new SalesActivityWindowRecord(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                name,
                activityType,
                categoryScope,
                dateFrom,
                dateTo,
                factor,
                enabled,
                versionNo,
                createdBy,
                updatedBy,
                operationsConfigBundleVersionId,
                operationsConfigBundleVersionNo,
                operationsConfigSourceRole,
                operationsConfigSourceLabel
        );
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getName() { return name; }
    public String getActivityType() { return activityType; }
    public String getCategoryScope() { return categoryScope; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public BigDecimal getFactor() { return factor; }
    public boolean isEnabled() { return enabled; }
    public int getVersionNo() { return versionNo; }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public Long getOperationsConfigBundleVersionId() { return operationsConfigBundleVersionId; }
    public String getOperationsConfigBundleVersionNo() { return operationsConfigBundleVersionNo; }
    public String getOperationsConfigSourceRole() { return operationsConfigSourceRole; }
    public String getOperationsConfigSourceLabel() { return operationsConfigSourceLabel; }
}
