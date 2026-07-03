package com.nuono.next.productlisting;

import java.math.BigDecimal;

public class ProductListingOfferStockWriteRequest {

    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String idPartner;
    private Long draftId;
    private Long dryRunTaskId;
    private Long realRunTaskId;
    private Long submittedBy;
    private String partnerSku;
    private String skuParent;
    private String pskuCode;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private BigDecimal salePrice;
    private String saleStart;
    private String saleEnd;
    private Boolean fbp;
    private String warehouseId;
    private String warehouseCode;
    private Integer quantity;
    private Boolean isActive;
    private String offerNote;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getIdPartner() {
        return idPartner;
    }

    public void setIdPartner(String idPartner) {
        this.idPartner = idPartner;
    }

    public Long getDraftId() {
        return draftId;
    }

    public void setDraftId(Long draftId) {
        this.draftId = draftId;
    }

    public Long getDryRunTaskId() {
        return dryRunTaskId;
    }

    public void setDryRunTaskId(Long dryRunTaskId) {
        this.dryRunTaskId = dryRunTaskId;
    }

    public Long getRealRunTaskId() {
        return realRunTaskId;
    }

    public void setRealRunTaskId(Long realRunTaskId) {
        this.realRunTaskId = realRunTaskId;
    }

    public Long getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(Long submittedBy) {
        this.submittedBy = submittedBy;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getSkuParent() {
        return skuParent;
    }

    public void setSkuParent(String skuParent) {
        this.skuParent = skuParent;
    }

    public String getPskuCode() {
        return pskuCode;
    }

    public void setPskuCode(String pskuCode) {
        this.pskuCode = pskuCode;
    }

    public BigDecimal getPriceMin() {
        return priceMin;
    }

    public void setPriceMin(BigDecimal priceMin) {
        this.priceMin = priceMin;
    }

    public BigDecimal getPriceMax() {
        return priceMax;
    }

    public void setPriceMax(BigDecimal priceMax) {
        this.priceMax = priceMax;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public String getSaleStart() {
        return saleStart;
    }

    public void setSaleStart(String saleStart) {
        this.saleStart = saleStart;
    }

    public String getSaleEnd() {
        return saleEnd;
    }

    public void setSaleEnd(String saleEnd) {
        this.saleEnd = saleEnd;
    }

    public Boolean getFbp() {
        return fbp;
    }

    public void setFbp(Boolean fbp) {
        this.fbp = fbp;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getOfferNote() {
        return offerNote;
    }

    public void setOfferNote(String offerNote) {
        this.offerNote = offerNote;
    }
}
