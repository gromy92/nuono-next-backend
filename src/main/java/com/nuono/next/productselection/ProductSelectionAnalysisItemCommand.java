package com.nuono.next.productselection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductSelectionAnalysisItemCommand {

    private Long operatorUserId;
    private List<String> sourceCollectionIds = new ArrayList<>();
    private String projectId;
    private String projectName;
    private String ali1688PurchaseUrl;
    private BigDecimal purchasePrice;

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public List<String> getSourceCollectionIds() {
        return sourceCollectionIds;
    }

    public void setSourceCollectionIds(List<String> sourceCollectionIds) {
        this.sourceCollectionIds = sourceCollectionIds;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
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
