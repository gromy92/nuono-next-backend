package com.nuono.next.productselection;

import java.math.BigDecimal;

public class ProductSelectionGroupProcurementRow {

    private Long groupId;
    private String ali1688PurchaseUrl;
    private BigDecimal purchasePriceRmb;
    private String procurementStatus;
    private Long createdBy;
    private Long updatedBy;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getAli1688PurchaseUrl() {
        return ali1688PurchaseUrl;
    }

    public void setAli1688PurchaseUrl(String ali1688PurchaseUrl) {
        this.ali1688PurchaseUrl = ali1688PurchaseUrl;
    }

    public BigDecimal getPurchasePriceRmb() {
        return purchasePriceRmb;
    }

    public void setPurchasePriceRmb(BigDecimal purchasePriceRmb) {
        this.purchasePriceRmb = purchasePriceRmb;
    }

    public String getProcurementStatus() {
        return procurementStatus;
    }

    public void setProcurementStatus(String procurementStatus) {
        this.procurementStatus = procurementStatus;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }
}
