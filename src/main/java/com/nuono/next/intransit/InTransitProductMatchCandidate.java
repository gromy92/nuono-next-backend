package com.nuono.next.intransit;

import java.math.BigDecimal;

public class InTransitProductMatchCandidate {
    private Long id;
    private Long ownerUserId;
    private Long batchId;
    private Long packageId;
    private String boxNo;
    private String sourceBarcode;
    private String sourcePsku;
    private String sourceMsku;
    private String productName;
    private String storeCode;
    private String siteCode;
    private Integer shippedQuantity;
    private Integer receivedQuantity;
    private Integer cartonCount;
    private Integer unitsPerCarton;
    private BigDecimal cartonWeightKg;
    private BigDecimal cartonVolumeCbm;
    private String matchStatus;
    private String matchMessage;
    private Long createdBy;
    private Long updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public String getBoxNo() { return boxNo; }
    public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
    public String getSourceBarcode() { return sourceBarcode; }
    public void setSourceBarcode(String sourceBarcode) { this.sourceBarcode = sourceBarcode; }
    public String getSourcePsku() { return sourcePsku; }
    public void setSourcePsku(String sourcePsku) { this.sourcePsku = sourcePsku; }
    public String getSourceMsku() { return sourceMsku; }
    public void setSourceMsku(String sourceMsku) { this.sourceMsku = sourceMsku; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public Integer getShippedQuantity() { return shippedQuantity; }
    public void setShippedQuantity(Integer shippedQuantity) { this.shippedQuantity = shippedQuantity; }
    public Integer getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(Integer receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public Integer getCartonCount() { return cartonCount; }
    public void setCartonCount(Integer cartonCount) { this.cartonCount = cartonCount; }
    public Integer getUnitsPerCarton() { return unitsPerCarton; }
    public void setUnitsPerCarton(Integer unitsPerCarton) { this.unitsPerCarton = unitsPerCarton; }
    public BigDecimal getCartonWeightKg() { return cartonWeightKg; }
    public void setCartonWeightKg(BigDecimal cartonWeightKg) { this.cartonWeightKg = cartonWeightKg; }
    public BigDecimal getCartonVolumeCbm() { return cartonVolumeCbm; }
    public void setCartonVolumeCbm(BigDecimal cartonVolumeCbm) { this.cartonVolumeCbm = cartonVolumeCbm; }
    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
    public String getMatchMessage() { return matchMessage; }
    public void setMatchMessage(String matchMessage) { this.matchMessage = matchMessage; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
