package com.nuono.next.noonpull;

import java.time.LocalDate;
import java.util.List;

public class NoonPullSmokeRunCommand {
    private String targetEnvironment;
    private Long ownerUserId;
    private String projectCode;
    private String projectName;
    private String storeCode;
    private String siteCode;
    private LocalDate salesDate;
    private LocalDate salesDateFrom;
    private LocalDate salesDateTo;
    private NoonSalesSmokeSource salesSource;
    private LocalDate orderDateFrom;
    private LocalDate orderDateTo;
    private LocalDate financeDateFrom;
    private LocalDate financeDateTo;
    private LocalDate advertisingDateFrom;
    private LocalDate advertisingDateTo;
    private LocalDate officialWarehouseFbnDateFrom;
    private LocalDate officialWarehouseFbnDateTo;
    private List<NoonPullDataDomain> dataDomains;
    private String rollbackOrGlobalPauseStrategy;

    public String getTargetEnvironment() {
        return targetEnvironment;
    }

    public void setTargetEnvironment(String targetEnvironment) {
        this.targetEnvironment = targetEnvironment;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
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

    public LocalDate getSalesDate() {
        return salesDate;
    }

    public void setSalesDate(LocalDate salesDate) {
        this.salesDate = salesDate;
    }

    public LocalDate getSalesDateFrom() {
        return salesDateFrom;
    }

    public void setSalesDateFrom(LocalDate salesDateFrom) {
        this.salesDateFrom = salesDateFrom;
    }

    public LocalDate getSalesDateTo() {
        return salesDateTo;
    }

    public void setSalesDateTo(LocalDate salesDateTo) {
        this.salesDateTo = salesDateTo;
    }

    public NoonSalesSmokeSource getSalesSource() {
        return salesSource == null ? NoonSalesSmokeSource.REPORT : salesSource;
    }

    public void setSalesSource(NoonSalesSmokeSource salesSource) {
        this.salesSource = salesSource;
    }

    public LocalDate getOrderDateFrom() {
        return orderDateFrom;
    }

    public void setOrderDateFrom(LocalDate orderDateFrom) {
        this.orderDateFrom = orderDateFrom;
    }

    public LocalDate getOrderDateTo() {
        return orderDateTo;
    }

    public void setOrderDateTo(LocalDate orderDateTo) {
        this.orderDateTo = orderDateTo;
    }

    public LocalDate getFinanceDateFrom() {
        return financeDateFrom;
    }

    public void setFinanceDateFrom(LocalDate financeDateFrom) {
        this.financeDateFrom = financeDateFrom;
    }

    public LocalDate getFinanceDateTo() {
        return financeDateTo;
    }

    public void setFinanceDateTo(LocalDate financeDateTo) {
        this.financeDateTo = financeDateTo;
    }

    public LocalDate getAdvertisingDateFrom() {
        return advertisingDateFrom;
    }

    public void setAdvertisingDateFrom(LocalDate advertisingDateFrom) {
        this.advertisingDateFrom = advertisingDateFrom;
    }

    public LocalDate getAdvertisingDateTo() {
        return advertisingDateTo;
    }

    public void setAdvertisingDateTo(LocalDate advertisingDateTo) {
        this.advertisingDateTo = advertisingDateTo;
    }

    public LocalDate getOfficialWarehouseFbnDateFrom() {
        return officialWarehouseFbnDateFrom;
    }

    public void setOfficialWarehouseFbnDateFrom(LocalDate officialWarehouseFbnDateFrom) {
        this.officialWarehouseFbnDateFrom = officialWarehouseFbnDateFrom;
    }

    public LocalDate getOfficialWarehouseFbnDateTo() {
        return officialWarehouseFbnDateTo;
    }

    public void setOfficialWarehouseFbnDateTo(LocalDate officialWarehouseFbnDateTo) {
        this.officialWarehouseFbnDateTo = officialWarehouseFbnDateTo;
    }

    public List<NoonPullDataDomain> getDataDomains() {
        return dataDomains;
    }

    public void setDataDomains(List<NoonPullDataDomain> dataDomains) {
        this.dataDomains = dataDomains;
    }

    public List<NoonPullDataDomain> requestedDataDomains() {
        if (dataDomains == null || dataDomains.isEmpty()) {
            return List.of(NoonPullDataDomain.PRODUCT, NoonPullDataDomain.SALES, NoonPullDataDomain.ORDER);
        }
        return dataDomains;
    }

    public String getRollbackOrGlobalPauseStrategy() {
        return rollbackOrGlobalPauseStrategy;
    }

    public void setRollbackOrGlobalPauseStrategy(String rollbackOrGlobalPauseStrategy) {
        this.rollbackOrGlobalPauseStrategy = rollbackOrGlobalPauseStrategy;
    }
}
