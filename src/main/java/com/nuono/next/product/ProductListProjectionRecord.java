package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;

public class ProductListProjectionRecord {

    private String skuParent;
    private String productSourceType;
    private String partnerSku;
    private String pskuCode;
    private String offerCode;
    private String title;
    private String titleCn;
    private String brand;
    private String imageUrl;
    private String barcode;
    private String referencePrice;
    private String originalPrice;
    private String salePrice;
    private String productFulltype;
    private Integer variantCount;
    private Integer siteOfferCount;
    private String siteLabelsCsv;
    private String liveStatusesCsv;
    private Integer totalFbnStock;
    private Integer totalSupermallStock;
    private Integer totalFbpStock;
    private Long viewsCount;
    private Long unitsSold;
    private String salesAmount;
    private String salesCurrency;
    private String skuGroup;
    private String groupRef;
    private String groupRefCanonical;
    private Integer issueCount;
    private String issueTagsCsv;
    private Integer currentSiteActiveFlag;
    private String currentSiteLiveStatus;
    private String currentSiteStatusCode;
    private String listingStartedAt;
    private String listingStartedSource;
    private String syncStatus;
    private String lastSyncedAt;
    private String lastDraftSavedAt;
    private String detailBaselineStatus;
    private String detailBaselineSyncedAt;
    private Integer productVariantSpecTotalCount;
    private Integer productVariantSpecReadyCount;
    private Integer productVariantSpecMaintainedCount;

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

    public String getSiteLabelsCsv() {
        return siteLabelsCsv;
    }

    public void setSiteLabelsCsv(String siteLabelsCsv) {
        this.siteLabelsCsv = siteLabelsCsv;
    }

    public String getLiveStatusesCsv() {
        return liveStatusesCsv;
    }

    public void setLiveStatusesCsv(String liveStatusesCsv) {
        this.liveStatusesCsv = liveStatusesCsv;
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

    public Integer getIssueCount() {
        return issueCount;
    }

    public void setIssueCount(Integer issueCount) {
        this.issueCount = issueCount;
    }

    public String getIssueTagsCsv() {
        return issueTagsCsv;
    }

    public void setIssueTagsCsv(String issueTagsCsv) {
        this.issueTagsCsv = issueTagsCsv;
    }

    public Integer getCurrentSiteActiveFlag() {
        return currentSiteActiveFlag;
    }

    public void setCurrentSiteActiveFlag(Integer currentSiteActiveFlag) {
        this.currentSiteActiveFlag = currentSiteActiveFlag;
    }

    public String getCurrentSiteLiveStatus() {
        return currentSiteLiveStatus;
    }

    public void setCurrentSiteLiveStatus(String currentSiteLiveStatus) {
        this.currentSiteLiveStatus = currentSiteLiveStatus;
    }

    public String getCurrentSiteStatusCode() {
        return currentSiteStatusCode;
    }

    public void setCurrentSiteStatusCode(String currentSiteStatusCode) {
        this.currentSiteStatusCode = currentSiteStatusCode;
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

    public String getDetailBaselineSyncedAt() {
        return detailBaselineSyncedAt;
    }

    public void setDetailBaselineSyncedAt(String detailBaselineSyncedAt) {
        this.detailBaselineSyncedAt = detailBaselineSyncedAt;
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

    public List<String> siteLabels() {
        return splitCsv(siteLabelsCsv);
    }

    public List<String> liveStatuses() {
        return splitCsv(liveStatusesCsv);
    }

    public List<String> issueTags() {
        return splitCsv(issueTagsCsv);
    }

    private List<String> splitCsv(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return parts;
        }
        for (String part : value.split(",")) {
            String normalized = part == null ? null : part.trim();
            if (normalized != null && !normalized.isEmpty()) {
                parts.add(normalized);
            }
        }
        return parts;
    }
}
