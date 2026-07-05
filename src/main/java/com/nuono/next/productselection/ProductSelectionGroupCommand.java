package com.nuono.next.productselection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductSelectionGroupCommand {

    private Long operatorUserId;
    private String storeCode;
    private List<String> sourceCollectionIds = new ArrayList<>();
    private String groupName;
    private String ali1688PurchaseUrl;
    private BigDecimal purchasePrice;

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public List<String> getSourceCollectionIds() {
        return sourceCollectionIds;
    }

    public void setSourceCollectionIds(List<String> sourceCollectionIds) {
        this.sourceCollectionIds = sourceCollectionIds;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getAli1688PurchaseUrl() {
        return ali1688PurchaseUrl;
    }

    public void setAli1688PurchaseUrl(String ali1688PurchaseUrl) {
        this.ali1688PurchaseUrl = ali1688PurchaseUrl;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }
}
