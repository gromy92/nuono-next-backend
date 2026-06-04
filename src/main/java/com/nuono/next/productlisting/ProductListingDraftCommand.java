package com.nuono.next.productlisting;

import java.math.BigDecimal;
import java.util.List;

public class ProductListingDraftCommand {

    private Long draftId;
    private String storeCode;
    private String psku;
    private Long idProductFullType;
    private String productFullType;
    private String family;
    private String productType;
    private String productSubType;
    private String productBrand;
    private String productBrandCode;
    private String productTitleEn;
    private String productTitleAr;
    private List<String> imageUrls;
    private BigDecimal price;
    private BigDecimal purchasePrice;
    private String supplyEvidenceType;
    private Long supplyEvidenceRefId;
    private Long optionalPurchaseOrderId;
    private Boolean fbp;
    private String warehouseId;
    private String warehouseCode;
    private Integer quantity;
    private Integer idWarranty;
    private String barcode;

    public Long getDraftId() {
        return draftId;
    }

    public void setDraftId(Long draftId) {
        this.draftId = draftId;
    }

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

    public String getProductFullType() {
        return productFullType;
    }

    public void setProductFullType(String productFullType) {
        this.productFullType = productFullType;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getProductSubType() {
        return productSubType;
    }

    public void setProductSubType(String productSubType) {
        this.productSubType = productSubType;
    }

    public String getProductBrand() {
        return productBrand;
    }

    public void setProductBrand(String productBrand) {
        this.productBrand = productBrand;
    }

    public String getProductBrandCode() {
        return productBrandCode;
    }

    public void setProductBrandCode(String productBrandCode) {
        this.productBrandCode = productBrandCode;
    }

    public String getProductTitleEn() {
        return productTitleEn;
    }

    public void setProductTitleEn(String productTitleEn) {
        this.productTitleEn = productTitleEn;
    }

    public String getProductTitleAr() {
        return productTitleAr;
    }

    public void setProductTitleAr(String productTitleAr) {
        this.productTitleAr = productTitleAr;
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

    public Long getSupplyEvidenceRefId() {
        return supplyEvidenceRefId;
    }

    public void setSupplyEvidenceRefId(Long supplyEvidenceRefId) {
        this.supplyEvidenceRefId = supplyEvidenceRefId;
    }

    public Long getOptionalPurchaseOrderId() {
        return optionalPurchaseOrderId;
    }

    public void setOptionalPurchaseOrderId(Long optionalPurchaseOrderId) {
        this.optionalPurchaseOrderId = optionalPurchaseOrderId;
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

    public Integer getIdWarranty() {
        return idWarranty;
    }

    public void setIdWarranty(Integer idWarranty) {
        this.idWarranty = idWarranty;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }
}
