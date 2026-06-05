package com.nuono.next.sales;

public class SalesSyncTaskRequest {

    private Long logicalStoreId;
    private String storeCode;
    private String siteCode;
    private String dateFrom;
    private String dateTo;
    private String listingCoverageMode;

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public void setLogicalStoreId(Long logicalStoreId) {
        this.logicalStoreId = logicalStoreId;
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

    public String getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(String dateFrom) {
        this.dateFrom = dateFrom;
    }

    public String getDateTo() {
        return dateTo;
    }

    public void setDateTo(String dateTo) {
        this.dateTo = dateTo;
    }

    public String getListingCoverageMode() {
        return listingCoverageMode;
    }

    public void setListingCoverageMode(String listingCoverageMode) {
        this.listingCoverageMode = listingCoverageMode;
    }
}
