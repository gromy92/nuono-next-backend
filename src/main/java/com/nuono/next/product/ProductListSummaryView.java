package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProductListSummaryView {

    private boolean ready;
    private String source;
    private String message;
    private List<String> warnings = new ArrayList<>();
    private String storeCode;
    private String skuParent;
    private String productSourceType;
    private String partnerSku;
    private String pskuCode;
    private String offerCode;
    private String title;
    private String titleCn;
    private String brand;
    private String imageUrl;
    private List<String> galleryImages = new ArrayList<>();
    private String barcode;
    private String referencePrice;
    private String originalPrice;
    private String salePrice;
    private String productFulltype;
    private String skuGroup;
    private String groupRef;
    private String groupRefCanonical;
    private Boolean isActive;
    private String liveStatus;
    private String statusCode;
    private String listingStartedAt;
    private String listingStartedSource;
    private String syncStatus;
    private String lastSyncedAt;
    private String lastDraftSavedAt;
    private String detailBaselineStatus;
    private String detailBaselineMessage;
    private String detailBaselineSyncedAt;
    private String productVariantSpecStatus;
    private Integer productVariantSpecTotalCount;
    private Integer productVariantSpecReadyCount;
    private Integer productVariantSpecMaintainedCount;
    private Integer variantCount;
    private Integer siteOfferCount;
    private List<String> siteLabels = new ArrayList<>();
    private List<String> liveStatuses = new ArrayList<>();
    private Boolean historyMetaReady;
    private Integer pendingKeyContentHistoryCount;
    private Integer visibleKeyContentHistoryCount;
    private String pendingKeyContentHistoryVisibleAfter;
    private Integer totalFbnStock;
    private Integer totalSupermallStock;
    private Integer totalFbpStock;
    private Long viewsCount;
    private Long unitsSold;
    private String salesAmount;
    private String salesCurrency;
    private Integer issueCount;
    private List<String> issueTags = new ArrayList<>();
    private Map<String, Object> lastPublishTask;

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSkuParent() {
        return skuParent;
    }

    public void setSkuParent(String skuParent) {
        this.skuParent = skuParent;
    }

    public String getProductSourceType() {
        return productSourceType;
    }

    public void setProductSourceType(String productSourceType) {
        this.productSourceType = productSourceType;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getPskuCode() {
        return pskuCode;
    }

    public void setPskuCode(String pskuCode) {
        this.pskuCode = pskuCode;
    }

    public String getOfferCode() {
        return offerCode;
    }

    public void setOfferCode(String offerCode) {
        this.offerCode = offerCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleCn() {
        return titleCn;
    }

    public void setTitleCn(String titleCn) {
        this.titleCn = titleCn;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = ProductImageUrlSupport.normalize(imageUrl);
    }

    public List<String> getGalleryImages() {
        return galleryImages;
    }

    public void setGalleryImages(List<String> galleryImages) {
        this.galleryImages = galleryImages == null ? new ArrayList<>() : galleryImages;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getReferencePrice() {
        return referencePrice;
    }

    public void setReferencePrice(String referencePrice) {
        this.referencePrice = referencePrice;
    }

    public String getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(String originalPrice) {
        this.originalPrice = originalPrice;
    }

    public String getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(String salePrice) {
        this.salePrice = salePrice;
    }

    public String getProductFulltype() {
        return productFulltype;
    }

    public void setProductFulltype(String productFulltype) {
        this.productFulltype = productFulltype;
    }

    public String getSkuGroup() {
        return skuGroup;
    }

    public void setSkuGroup(String skuGroup) {
        this.skuGroup = skuGroup;
    }

    public String getGroupRef() {
        return groupRef;
    }

    public void setGroupRef(String groupRef) {
        this.groupRef = groupRef;
    }

    public String getGroupRefCanonical() {
        return groupRefCanonical;
    }

    public void setGroupRefCanonical(String groupRefCanonical) {
        this.groupRefCanonical = groupRefCanonical;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getLiveStatus() {
        return liveStatus;
    }

    public void setLiveStatus(String liveStatus) {
        this.liveStatus = liveStatus;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getListingStartedAt() {
        return listingStartedAt;
    }

    public void setListingStartedAt(String listingStartedAt) {
        this.listingStartedAt = listingStartedAt;
    }

    public String getListingStartedSource() {
        return listingStartedSource;
    }

    public void setListingStartedSource(String listingStartedSource) {
        this.listingStartedSource = listingStartedSource;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(String lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getLastDraftSavedAt() {
        return lastDraftSavedAt;
    }

    public void setLastDraftSavedAt(String lastDraftSavedAt) {
        this.lastDraftSavedAt = lastDraftSavedAt;
    }

    public String getDetailBaselineStatus() {
        return detailBaselineStatus;
    }

    public void setDetailBaselineStatus(String detailBaselineStatus) {
        this.detailBaselineStatus = detailBaselineStatus;
    }

    public String getDetailBaselineMessage() {
        return detailBaselineMessage;
    }

    public void setDetailBaselineMessage(String detailBaselineMessage) {
        this.detailBaselineMessage = detailBaselineMessage;
    }

    public String getDetailBaselineSyncedAt() {
        return detailBaselineSyncedAt;
    }

    public void setDetailBaselineSyncedAt(String detailBaselineSyncedAt) {
        this.detailBaselineSyncedAt = detailBaselineSyncedAt;
    }

    public String getProductVariantSpecStatus() {
        return productVariantSpecStatus;
    }

    public void setProductVariantSpecStatus(String productVariantSpecStatus) {
        this.productVariantSpecStatus = productVariantSpecStatus;
    }

    public Integer getProductVariantSpecTotalCount() {
        return productVariantSpecTotalCount;
    }

    public void setProductVariantSpecTotalCount(Integer productVariantSpecTotalCount) {
        this.productVariantSpecTotalCount = productVariantSpecTotalCount;
    }

    public Integer getProductVariantSpecReadyCount() {
        return productVariantSpecReadyCount;
    }

    public void setProductVariantSpecReadyCount(Integer productVariantSpecReadyCount) {
        this.productVariantSpecReadyCount = productVariantSpecReadyCount;
    }

    public Integer getProductVariantSpecMaintainedCount() {
        return productVariantSpecMaintainedCount;
    }

    public void setProductVariantSpecMaintainedCount(Integer productVariantSpecMaintainedCount) {
        this.productVariantSpecMaintainedCount = productVariantSpecMaintainedCount;
    }

    public Integer getVariantCount() {
        return variantCount;
    }

    public void setVariantCount(Integer variantCount) {
        this.variantCount = variantCount;
    }

    public Integer getSiteOfferCount() {
        return siteOfferCount;
    }

    public void setSiteOfferCount(Integer siteOfferCount) {
        this.siteOfferCount = siteOfferCount;
    }

    public List<String> getSiteLabels() {
        return siteLabels;
    }

    public void setSiteLabels(List<String> siteLabels) {
        this.siteLabels = siteLabels;
    }

    public List<String> getLiveStatuses() {
        return liveStatuses;
    }

    public void setLiveStatuses(List<String> liveStatuses) {
        this.liveStatuses = liveStatuses;
    }

    public Boolean getHistoryMetaReady() {
        return historyMetaReady;
    }

    public void setHistoryMetaReady(Boolean historyMetaReady) {
        this.historyMetaReady = historyMetaReady;
    }

    public Integer getPendingKeyContentHistoryCount() {
        return pendingKeyContentHistoryCount;
    }

    public void setPendingKeyContentHistoryCount(Integer pendingKeyContentHistoryCount) {
        this.pendingKeyContentHistoryCount = pendingKeyContentHistoryCount;
    }

    public Integer getVisibleKeyContentHistoryCount() {
        return visibleKeyContentHistoryCount;
    }

    public void setVisibleKeyContentHistoryCount(Integer visibleKeyContentHistoryCount) {
        this.visibleKeyContentHistoryCount = visibleKeyContentHistoryCount;
    }

    public String getPendingKeyContentHistoryVisibleAfter() {
        return pendingKeyContentHistoryVisibleAfter;
    }

    public void setPendingKeyContentHistoryVisibleAfter(String pendingKeyContentHistoryVisibleAfter) {
        this.pendingKeyContentHistoryVisibleAfter = pendingKeyContentHistoryVisibleAfter;
    }

    public Integer getTotalFbnStock() {
        return totalFbnStock;
    }

    public void setTotalFbnStock(Integer totalFbnStock) {
        this.totalFbnStock = totalFbnStock;
    }

    public Integer getTotalSupermallStock() {
        return totalSupermallStock;
    }

    public void setTotalSupermallStock(Integer totalSupermallStock) {
        this.totalSupermallStock = totalSupermallStock;
    }

    public Integer getTotalFbpStock() {
        return totalFbpStock;
    }

    public void setTotalFbpStock(Integer totalFbpStock) {
        this.totalFbpStock = totalFbpStock;
    }

    public Long getViewsCount() {
        return viewsCount;
    }

    public void setViewsCount(Long viewsCount) {
        this.viewsCount = viewsCount;
    }

    public Long getUnitsSold() {
        return unitsSold;
    }

    public void setUnitsSold(Long unitsSold) {
        this.unitsSold = unitsSold;
    }

    public String getSalesAmount() {
        return salesAmount;
    }

    public void setSalesAmount(String salesAmount) {
        this.salesAmount = salesAmount;
    }

    public String getSalesCurrency() {
        return salesCurrency;
    }

    public void setSalesCurrency(String salesCurrency) {
        this.salesCurrency = salesCurrency;
    }

    public Integer getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(Integer issueCount) {
        this.issueCount = issueCount;
    }

    public List<String> getIssueTags() {
        return issueTags;
    }

    public void setIssueTags(List<String> issueTags) {
        this.issueTags = issueTags == null ? new ArrayList<>() : issueTags;
    }

    public Map<String, Object> getLastPublishTask() {
        return lastPublishTask;
    }

    public void setLastPublishTask(Map<String, Object> lastPublishTask) {
        this.lastPublishTask = lastPublishTask;
    }
}
