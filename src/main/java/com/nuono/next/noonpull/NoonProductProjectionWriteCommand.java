package com.nuono.next.noonpull;

import com.nuono.next.product.ProductProjectionPersistenceService;
import java.util.ArrayList;
import java.util.List;

public class NoonProductProjectionWriteCommand {
    private Long ownerUserId;
    private String projectCode;
    private String projectName;
    private String referenceStoreCode;
    private String sourceBatchId;
    private boolean preserveDrafts = true;
    private boolean publishFlowTriggered;
    private boolean completeSiteScope;
    private List<ProductProjectionPersistenceService.SiteSeed> siteSeeds = new ArrayList<>();
    private List<ProductProjectionPersistenceService.ProductMasterSeed> productSeeds = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

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

    public String getReferenceStoreCode() {
        return referenceStoreCode;
    }

    public void setReferenceStoreCode(String referenceStoreCode) {
        this.referenceStoreCode = referenceStoreCode;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(String sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }

    public boolean isPreserveDrafts() {
        return preserveDrafts;
    }

    public void setPreserveDrafts(boolean preserveDrafts) {
        this.preserveDrafts = preserveDrafts;
    }

    public boolean isPublishFlowTriggered() {
        return publishFlowTriggered;
    }

    public void setPublishFlowTriggered(boolean publishFlowTriggered) {
        this.publishFlowTriggered = publishFlowTriggered;
    }

    public boolean isCompleteSiteScope() {
        return completeSiteScope;
    }

    public void setCompleteSiteScope(boolean completeSiteScope) {
        this.completeSiteScope = completeSiteScope;
    }

    public List<ProductProjectionPersistenceService.SiteSeed> getSiteSeeds() {
        return siteSeeds;
    }

    public void setSiteSeeds(List<ProductProjectionPersistenceService.SiteSeed> siteSeeds) {
        this.siteSeeds = siteSeeds == null ? new ArrayList<>() : siteSeeds;
    }

    public List<ProductProjectionPersistenceService.ProductMasterSeed> getProductSeeds() {
        return productSeeds;
    }

    public void setProductSeeds(List<ProductProjectionPersistenceService.ProductMasterSeed> productSeeds) {
        this.productSeeds = productSeeds == null ? new ArrayList<>() : productSeeds;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }
}
