package com.nuono.next.productlisting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class ProductListingDraftCommand {

    private Long draftId;
    private String storeCode;
    private String sourceType;
    private Long sourceRefId;
    private Long rebuildSourceProductMasterId;
    private String inheritedListingStartedAt;
    private String inheritedListingStartedSource;
    private String psku;
    private Long idProductFullType;
    private String productFullType;
    private String family;
    private String productType;
    private String productSubType;
    private String productBrand;
    private String productBrandCode;
    private String productTitleCn;
    private String productTitleEn;
    private String productTitleAr;
    private String productDescriptionCn;
    private String productDescriptionEn;
    private String productDescriptionAr;
    private List<String> productHighlightsCn;
    private List<String> productHighlightsEn;
    private List<String> productHighlightsAr;
    private List<Map<String, Object>> keyAttributes;
    private List<String> imageUrls;
    private BigDecimal price;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private BigDecimal salePrice;
    private String saleStart;
    private String saleEnd;
    private BigDecimal purchasePrice;
    private String supplyEvidenceType;
    private Long supplyEvidenceRefId;
    private Long optionalPurchaseOrderId;
    private Boolean fbp;
    private String warehouseId;
    private String warehouseCode;
    private Integer quantity;
    private Integer idWarranty;
    private Boolean isActive;
    private String offerNote;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceRefId() {
        return sourceRefId;
    }

    public void setSourceRefId(Long sourceRefId) {
        this.sourceRefId = sourceRefId;
    }

    public Long getRebuildSourceProductMasterId() {
        return rebuildSourceProductMasterId;
    }

    public void setRebuildSourceProductMasterId(Long rebuildSourceProductMasterId) {
        this.rebuildSourceProductMasterId = rebuildSourceProductMasterId;
    }

    public String getInheritedListingStartedAt() {
        return inheritedListingStartedAt;
    }

    public void setInheritedListingStartedAt(String inheritedListingStartedAt) {
        this.inheritedListingStartedAt = inheritedListingStartedAt;
    }

    public String getInheritedListingStartedSource() {
        return inheritedListingStartedSource;
    }

    public void setInheritedListingStartedSource(String inheritedListingStartedSource) {
        this.inheritedListingStartedSource = inheritedListingStartedSource;
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

    public String getProductTitleCn() {
        return productTitleCn;
    }

    public void setProductTitleCn(String productTitleCn) {
        this.productTitleCn = productTitleCn;
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

    public String getProductDescriptionCn() {
        return productDescriptionCn;
    }

    public void setProductDescriptionCn(String productDescriptionCn) {
        this.productDescriptionCn = productDescriptionCn;
    }

    public String getProductDescriptionEn() {
        return productDescriptionEn;
    }

    public void setProductDescriptionEn(String productDescriptionEn) {
        this.productDescriptionEn = productDescriptionEn;
    }

    public String getProductDescriptionAr() {
        return productDescriptionAr;
    }

    public void setProductDescriptionAr(String productDescriptionAr) {
        this.productDescriptionAr = productDescriptionAr;
    }

    public List<String> getProductHighlightsCn() {
        return productHighlightsCn;
    }

    public void setProductHighlightsCn(List<String> productHighlightsCn) {
        this.productHighlightsCn = productHighlightsCn;
    }

    public List<String> getProductHighlightsEn() {
        return productHighlightsEn;
    }

    public void setProductHighlightsEn(List<String> productHighlightsEn) {
        this.productHighlightsEn = productHighlightsEn;
    }

    public List<String> getProductHighlightsAr() {
        return productHighlightsAr;
    }

    public void setProductHighlightsAr(List<String> productHighlightsAr) {
        this.productHighlightsAr = productHighlightsAr;
    }

    public List<Map<String, Object>> getKeyAttributes() {
        return keyAttributes;
    }

    public void setKeyAttributes(List<Map<String, Object>> keyAttributes) {
        this.keyAttributes = keyAttributes;
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

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }
}
