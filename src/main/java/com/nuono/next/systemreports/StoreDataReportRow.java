package com.nuono.next.systemreports;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StoreDataReportRow {

    private Long ownerUserId;
    private Long logicalStoreId;
    private String projectCode;
    private String projectName;
    private String storeCode;
    private String siteCode;
    private String siteStatus;
    private int siteOfferCount;
    private int crossStoreOfferCount;
    private int missingDetailBaselineCount;
    private int missingTitleEnCount;
    private int missingDescriptionEnCount;
    private int missingBrandCount;
    private int missingProductFulltypeCount;
    private int missingImageCount;
    private int offersWithSalesFacts;
    private int offersWithoutSalesFacts;
    private int salesProductKeyCount;
    private int salesKeysWithoutOfferCount;
    private int salesFactRows;
    private LocalDate latestFactDate;
    private int salesImportBatchCount;
    private String latestSalesImportStatus;
    private LocalDateTime latestSalesImportedAt;
    private int lifecycleCurrentCount;
    private int lifecycleMissingCount;
    private int lifecycleDataInsufficientCount;
    private String detailState;
    private String salesState;
    private String lifecycleState;
    private String overallState;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public void setLogicalStoreId(Long logicalStoreId) {
        this.logicalStoreId = logicalStoreId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
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

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getSiteStatus() {
        return siteStatus;
    }

    public void setSiteStatus(String siteStatus) {
        this.siteStatus = siteStatus;
    }

    public int getSiteOfferCount() {
        return siteOfferCount;
    }

    public void setSiteOfferCount(int siteOfferCount) {
        this.siteOfferCount = siteOfferCount;
    }

    public int getCrossStoreOfferCount() {
        return crossStoreOfferCount;
    }

    public void setCrossStoreOfferCount(int crossStoreOfferCount) {
        this.crossStoreOfferCount = crossStoreOfferCount;
    }

    public int getMissingDetailBaselineCount() {
        return missingDetailBaselineCount;
    }

    public void setMissingDetailBaselineCount(int missingDetailBaselineCount) {
        this.missingDetailBaselineCount = missingDetailBaselineCount;
    }

    public int getMissingTitleEnCount() {
        return missingTitleEnCount;
    }

    public void setMissingTitleEnCount(int missingTitleEnCount) {
        this.missingTitleEnCount = missingTitleEnCount;
    }

    public int getMissingDescriptionEnCount() {
        return missingDescriptionEnCount;
    }

    public void setMissingDescriptionEnCount(int missingDescriptionEnCount) {
        this.missingDescriptionEnCount = missingDescriptionEnCount;
    }

    public int getMissingBrandCount() {
        return missingBrandCount;
    }

    public void setMissingBrandCount(int missingBrandCount) {
        this.missingBrandCount = missingBrandCount;
    }

    public int getMissingProductFulltypeCount() {
        return missingProductFulltypeCount;
    }

    public void setMissingProductFulltypeCount(int missingProductFulltypeCount) {
        this.missingProductFulltypeCount = missingProductFulltypeCount;
    }

    public int getMissingImageCount() {
        return missingImageCount;
    }

    public void setMissingImageCount(int missingImageCount) {
        this.missingImageCount = missingImageCount;
    }

    public int getOffersWithSalesFacts() {
        return offersWithSalesFacts;
    }

    public void setOffersWithSalesFacts(int offersWithSalesFacts) {
        this.offersWithSalesFacts = offersWithSalesFacts;
    }

    public int getOffersWithoutSalesFacts() {
        return offersWithoutSalesFacts;
    }

    public void setOffersWithoutSalesFacts(int offersWithoutSalesFacts) {
        this.offersWithoutSalesFacts = offersWithoutSalesFacts;
    }

    public int getSalesProductKeyCount() {
        return salesProductKeyCount;
    }

    public void setSalesProductKeyCount(int salesProductKeyCount) {
        this.salesProductKeyCount = salesProductKeyCount;
    }

    public int getSalesKeysWithoutOfferCount() {
        return salesKeysWithoutOfferCount;
    }

    public void setSalesKeysWithoutOfferCount(int salesKeysWithoutOfferCount) {
        this.salesKeysWithoutOfferCount = salesKeysWithoutOfferCount;
    }

    public int getSalesFactRows() {
        return salesFactRows;
    }

    public void setSalesFactRows(int salesFactRows) {
        this.salesFactRows = salesFactRows;
    }

    public LocalDate getLatestFactDate() {
        return latestFactDate;
    }

    public void setLatestFactDate(LocalDate latestFactDate) {
        this.latestFactDate = latestFactDate;
    }

    public int getSalesImportBatchCount() {
        return salesImportBatchCount;
    }

    public void setSalesImportBatchCount(int salesImportBatchCount) {
        this.salesImportBatchCount = salesImportBatchCount;
    }

    public String getLatestSalesImportStatus() {
        return latestSalesImportStatus;
    }

    public void setLatestSalesImportStatus(String latestSalesImportStatus) {
        this.latestSalesImportStatus = latestSalesImportStatus;
    }

    public LocalDateTime getLatestSalesImportedAt() {
        return latestSalesImportedAt;
    }

    public void setLatestSalesImportedAt(LocalDateTime latestSalesImportedAt) {
        this.latestSalesImportedAt = latestSalesImportedAt;
    }

    public int getLifecycleCurrentCount() {
        return lifecycleCurrentCount;
    }

    public void setLifecycleCurrentCount(int lifecycleCurrentCount) {
        this.lifecycleCurrentCount = lifecycleCurrentCount;
    }

    public int getLifecycleMissingCount() {
        return lifecycleMissingCount;
    }

    public void setLifecycleMissingCount(int lifecycleMissingCount) {
        this.lifecycleMissingCount = lifecycleMissingCount;
    }

    public int getLifecycleDataInsufficientCount() {
        return lifecycleDataInsufficientCount;
    }

    public void setLifecycleDataInsufficientCount(int lifecycleDataInsufficientCount) {
        this.lifecycleDataInsufficientCount = lifecycleDataInsufficientCount;
    }

    public String getDetailState() {
        return detailState;
    }

    public void setDetailState(String detailState) {
        this.detailState = detailState;
    }

    public String getSalesState() {
        return salesState;
    }

    public void setSalesState(String salesState) {
        this.salesState = salesState;
    }

    public String getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(String lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    public String getOverallState() {
        return overallState;
    }

    public void setOverallState(String overallState) {
        this.overallState = overallState;
    }
}
