package com.nuono.next.productselection;

import java.math.BigDecimal;

public class ProductSelectionGroupProcurementView {

    private String ali1688PurchaseUrl;
    private BigDecimal purchasePriceRmb;
    private String status;

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

    public BigDecimal getPurchasePrice() {
        return purchasePriceRmb;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
