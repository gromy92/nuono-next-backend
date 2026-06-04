package com.nuono.next.productlisting;

import java.math.BigDecimal;
import java.util.List;

public class ProductListingDraftCommand {

    private String storeCode;
    private String psku;
    private Long idProductFullType;
    private String productTitleEn;
    private List<String> imageUrls;
    private BigDecimal price;
    private BigDecimal purchasePrice;
    private String supplyEvidenceType;
    private Long optionalPurchaseOrderId;

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getPsku() {
        return psku;
    }

    public void setPsku(String psku) {
        this.psku = psku;
    }

    public Long getIdProductFullType() {
        return idProductFullType;
    }

    public void setIdProductFullType(Long idProductFullType) {
        this.idProductFullType = idProductFullType;
    }

    public String getProductTitleEn() {
        return productTitleEn;
    }

    public void setProductTitleEn(String productTitleEn) {
        this.productTitleEn = productTitleEn;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public String getSupplyEvidenceType() {
        return supplyEvidenceType;
    }

    public void setSupplyEvidenceType(String supplyEvidenceType) {
        this.supplyEvidenceType = supplyEvidenceType;
    }

    public Long getOptionalPurchaseOrderId() {
        return optionalPurchaseOrderId;
    }

    public void setOptionalPurchaseOrderId(Long optionalPurchaseOrderId) {
        this.optionalPurchaseOrderId = optionalPurchaseOrderId;
    }
}
