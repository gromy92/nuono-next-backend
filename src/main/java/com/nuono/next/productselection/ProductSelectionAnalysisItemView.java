package com.nuono.next.productselection;

import java.math.BigDecimal;

public class ProductSelectionAnalysisItemView {

    private String id;
    private String projectId;
    private String projectName;
    private Integer projectMaterialCount;
    private String sourceCollectionId;
    private String ali1688PurchaseUrl;
    private BigDecimal purchasePrice;
    private ProductSelectionSourceCollectionView sourceCollection;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Integer getProjectMaterialCount() {
        return projectMaterialCount;
    }

    public void setProjectMaterialCount(Integer projectMaterialCount) {
        this.projectMaterialCount = projectMaterialCount;
    }

    public String getSourceCollectionId() {
        return sourceCollectionId;
    }

    public void setSourceCollectionId(String sourceCollectionId) {
        this.sourceCollectionId = sourceCollectionId;
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

    public ProductSelectionSourceCollectionView getSourceCollection() {
        return sourceCollection;
    }

    public void setSourceCollection(ProductSelectionSourceCollectionView sourceCollection) {
        this.sourceCollection = sourceCollection;
    }
}
