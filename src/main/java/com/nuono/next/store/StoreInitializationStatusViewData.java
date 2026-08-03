package com.nuono.next.store;

import com.nuono.next.store.LocalDbStoreInitializationService.StoreInitializationProductListItemView;
import com.nuono.next.store.LocalDbStoreInitializationService.StoreInitializationProductSampleView;
import com.nuono.next.store.LocalDbStoreInitializationService.StoreInitializationSiteSummaryView;
import com.nuono.next.store.LocalDbStoreInitializationService.StoreInitializationStepView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data-only base kept separate from the store-initialization orchestration service. */
public class StoreInitializationStatusViewData {
    private String mode;
    private boolean ready;
    private String status;
    private String message;
    private Long ownerUserId;
    private String projectName;
    private String projectCode;
    private String storeCode;
    private Integer siteCount;
    private Integer uniqueProductCount;
    private Integer siteOfferCount;
    private Integer progressPercent;
    private String phaseLabel;
    private String startedAt;
    private String lastInitializedAt;
    private Integer noonRequestTotalCount = 0;
    private Map<String, Integer> noonRequestCounts = new LinkedHashMap<>();
    private Boolean canEnterProductWorkbench = false;
    private List<String> missingCoreTables = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<StoreInitializationStepView> steps = new ArrayList<>();
    private List<StoreInitializationSiteSummaryView> siteSummaries = new ArrayList<>();
    private List<StoreInitializationProductSampleView> sampleProducts = new ArrayList<>();
    private List<StoreInitializationProductListItemView> productItems = new ArrayList<>();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public Integer getSiteCount() { return siteCount; }
    public void setSiteCount(Integer siteCount) { this.siteCount = siteCount; }
    public Integer getUniqueProductCount() { return uniqueProductCount; }
    public void setUniqueProductCount(Integer uniqueProductCount) { this.uniqueProductCount = uniqueProductCount; }
    public Integer getSiteOfferCount() { return siteOfferCount; }
    public void setSiteOfferCount(Integer siteOfferCount) { this.siteOfferCount = siteOfferCount; }
    public Integer getProgressPercent() { return progressPercent; }
    public void setProgressPercent(Integer progressPercent) { this.progressPercent = progressPercent; }
    public String getPhaseLabel() { return phaseLabel; }
    public void setPhaseLabel(String phaseLabel) { this.phaseLabel = phaseLabel; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getLastInitializedAt() { return lastInitializedAt; }
    public void setLastInitializedAt(String lastInitializedAt) { this.lastInitializedAt = lastInitializedAt; }
    public Integer getNoonRequestTotalCount() { return noonRequestTotalCount; }
    public void setNoonRequestTotalCount(Integer value) { this.noonRequestTotalCount = value; }
    public Map<String, Integer> getNoonRequestCounts() { return noonRequestCounts; }
    public void setNoonRequestCounts(Map<String, Integer> value) { this.noonRequestCounts = value; }
    public Boolean getCanEnterProductWorkbench() { return canEnterProductWorkbench; }
    public void setCanEnterProductWorkbench(Boolean value) { this.canEnterProductWorkbench = value; }
    public List<String> getMissingCoreTables() { return missingCoreTables; }
    public void setMissingCoreTables(List<String> value) { this.missingCoreTables = value; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public List<StoreInitializationStepView> getSteps() { return steps; }
    public void setSteps(List<StoreInitializationStepView> steps) { this.steps = steps; }
    public List<StoreInitializationSiteSummaryView> getSiteSummaries() { return siteSummaries; }
    public void setSiteSummaries(List<StoreInitializationSiteSummaryView> value) { this.siteSummaries = value; }
    public List<StoreInitializationProductSampleView> getSampleProducts() { return sampleProducts; }
    public void setSampleProducts(List<StoreInitializationProductSampleView> value) { this.sampleProducts = value; }
    public List<StoreInitializationProductListItemView> getProductItems() { return productItems; }
    public void setProductItems(List<StoreInitializationProductListItemView> value) { this.productItems = value; }
}
