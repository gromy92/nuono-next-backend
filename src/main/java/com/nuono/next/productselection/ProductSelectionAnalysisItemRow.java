package com.nuono.next.productselection;

import java.math.BigDecimal;

public class ProductSelectionAnalysisItemRow extends ProductSelectionSourceCollectionRow {

    private Long analysisItemId;
    private Long projectId;
    private String projectName;
    private Integer projectMaterialCount;
    private Long sourceCollectionId;
    private String ali1688PurchaseUrl;
    private BigDecimal purchasePriceRmb;
    private String analysisStatus;

    public Long getAnalysisItemId() {
        return analysisItemId;
    }

    public void setAnalysisItemId(Long analysisItemId) {
        this.analysisItemId = analysisItemId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
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

    public Long getSourceCollectionId() {
        return sourceCollectionId;
    }

    public void setSourceCollectionId(Long sourceCollectionId) {
        this.sourceCollectionId = sourceCollectionId;
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

    public String getAnalysisStatus() {
        return analysisStatus;
    }

    public void setAnalysisStatus(String analysisStatus) {
        this.analysisStatus = analysisStatus;
    }
}
