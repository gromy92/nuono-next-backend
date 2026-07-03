package com.nuono.next.competitoranalysis;

import com.nuono.next.product.ProductImageUrlSupport;
import java.time.LocalDateTime;

public class CompetitorWatchProductView {
    private Long id;
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private Long productSiteOfferId;
    private String skuParent;
    private String partnerSku;
    private String childSku;
    private String pskuCode;
    private String selfNoonProductCode;
    private String selfCodeType;
    private String title;
    private String titleCn;
    private String brand;
    private String imageUrl;
    private String productFulltype;
    private String status;
    private Long latestRunId;
    private String latestRunStatus;
    private LocalDateTime latestRunAt;

    public static CompetitorWatchProductView fromRow(CompetitorWatchProductRow row) {
        if (row == null) {
            return null;
        }
        CompetitorWatchProductView view = new CompetitorWatchProductView();
        view.setId(row.getId());
        view.setOwnerUserId(row.getOwnerUserId());
        view.setStoreCode(row.getStoreCode());
        view.setSiteCode(row.getSiteCode());
        view.setProductSiteOfferId(row.getProductSiteOfferId());
        view.setSkuParent(row.getSkuParent());
        view.setPartnerSku(row.getPartnerSku());
        view.setChildSku(row.getChildSku());
        view.setPskuCode(row.getPskuCode());
        view.setSelfNoonProductCode(row.getSelfNoonProductCode());
        view.setSelfCodeType(row.getSelfCodeType());
        view.setTitle(row.getTitleSnapshot());
        view.setTitleCn(row.getTitleCnSnapshot());
        view.setBrand(row.getBrandSnapshot());
        view.setImageUrl(ProductImageUrlSupport.normalize(row.getImageUrlSnapshot()));
        view.setProductFulltype(row.getProductFulltypeSnapshot());
        view.setStatus(row.getStatus());
        view.setLatestRunId(row.getLatestRunId());
        view.setLatestRunStatus(row.getLatestRunStatus());
        view.setLatestRunAt(row.getLatestRunAt());
        return view;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public Long getProductSiteOfferId() { return productSiteOfferId; }
    public void setProductSiteOfferId(Long productSiteOfferId) { this.productSiteOfferId = productSiteOfferId; }
    public String getSkuParent() { return skuParent; }
    public void setSkuParent(String skuParent) { this.skuParent = skuParent; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getChildSku() { return childSku; }
    public void setChildSku(String childSku) { this.childSku = childSku; }
    public String getPskuCode() { return pskuCode; }
    public void setPskuCode(String pskuCode) { this.pskuCode = pskuCode; }
    public String getSelfNoonProductCode() { return selfNoonProductCode; }
    public void setSelfNoonProductCode(String selfNoonProductCode) { this.selfNoonProductCode = selfNoonProductCode; }
    public String getSelfCodeType() { return selfCodeType; }
    public void setSelfCodeType(String selfCodeType) { this.selfCodeType = selfCodeType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTitleCn() { return titleCn; }
    public void setTitleCn(String titleCn) { this.titleCn = titleCn; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getProductFulltype() { return productFulltype; }
    public void setProductFulltype(String productFulltype) { this.productFulltype = productFulltype; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLatestRunId() { return latestRunId; }
    public void setLatestRunId(Long latestRunId) { this.latestRunId = latestRunId; }
    public String getLatestRunStatus() { return latestRunStatus; }
    public void setLatestRunStatus(String latestRunStatus) { this.latestRunStatus = latestRunStatus; }
    public LocalDateTime getLatestRunAt() { return latestRunAt; }
    public void setLatestRunAt(LocalDateTime latestRunAt) { this.latestRunAt = latestRunAt; }
}
