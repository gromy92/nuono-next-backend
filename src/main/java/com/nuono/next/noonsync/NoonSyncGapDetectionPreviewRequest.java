package com.nuono.next.noonsync;

import java.time.LocalDate;

public class NoonSyncGapDetectionPreviewRequest {

    private Long ownerUserId;
    private Long logicalStoreId;
    private NoonSyncAccountOrigin accountOrigin;
    private Boolean noonBindingReady;
    private Boolean providerConfigured;
    private NoonProductWorkspaceState productWorkspaceState;
    private NoonSalesCoverageState salesCoverageState;
    private LocalDate salesBackfillFrom;
    private LocalDate salesBackfillTo;

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

    public NoonSyncAccountOrigin getAccountOrigin() {
        return accountOrigin;
    }

    public void setAccountOrigin(NoonSyncAccountOrigin accountOrigin) {
        this.accountOrigin = accountOrigin;
    }

    public Boolean getNoonBindingReady() {
        return noonBindingReady;
    }

    public void setNoonBindingReady(Boolean noonBindingReady) {
        this.noonBindingReady = noonBindingReady;
    }

    public Boolean getProviderConfigured() {
        return providerConfigured;
    }

    public void setProviderConfigured(Boolean providerConfigured) {
        this.providerConfigured = providerConfigured;
    }

    public NoonProductWorkspaceState getProductWorkspaceState() {
        return productWorkspaceState;
    }

    public void setProductWorkspaceState(NoonProductWorkspaceState productWorkspaceState) {
        this.productWorkspaceState = productWorkspaceState;
    }

    public NoonSalesCoverageState getSalesCoverageState() {
        return salesCoverageState;
    }

    public void setSalesCoverageState(NoonSalesCoverageState salesCoverageState) {
        this.salesCoverageState = salesCoverageState;
    }

    public LocalDate getSalesBackfillFrom() {
        return salesBackfillFrom;
    }

    public void setSalesBackfillFrom(LocalDate salesBackfillFrom) {
        this.salesBackfillFrom = salesBackfillFrom;
    }

    public LocalDate getSalesBackfillTo() {
        return salesBackfillTo;
    }

    public void setSalesBackfillTo(LocalDate salesBackfillTo) {
        this.salesBackfillTo = salesBackfillTo;
    }
}
