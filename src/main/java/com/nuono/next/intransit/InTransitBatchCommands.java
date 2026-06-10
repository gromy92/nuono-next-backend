package com.nuono.next.intransit;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class InTransitBatchCommands {

    private InTransitBatchCommands() {
    }

    public static class InTransitBatchQuery {
        private Long ownerUserId;
        private Long standardForwarderId;
        private String rawForwarderName;
        private String transportMode;
        private String targetStoreCode;
        private String targetSiteCode;
        private String targetWarehouseName;
        private String batchStatus;
        private String statusScope;
        private String skuKeyword;
        private LocalDate etaFrom;
        private LocalDate etaTo;
        private Integer limit;
        private boolean accessScopeRestricted;
        private List<InTransitStoreSiteScope> allowedStoreSites = Collections.emptyList();

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public Long getStandardForwarderId() {
            return standardForwarderId;
        }

        public void setStandardForwarderId(Long standardForwarderId) {
            this.standardForwarderId = standardForwarderId;
        }

        public String getRawForwarderName() {
            return rawForwarderName;
        }

        public void setRawForwarderName(String rawForwarderName) {
            this.rawForwarderName = rawForwarderName;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode;
        }

        public String getTargetStoreCode() {
            return targetStoreCode;
        }

        public void setTargetStoreCode(String targetStoreCode) {
            this.targetStoreCode = targetStoreCode;
        }

        public String getTargetSiteCode() {
            return targetSiteCode;
        }

        public void setTargetSiteCode(String targetSiteCode) {
            this.targetSiteCode = targetSiteCode;
        }

        public String getTargetWarehouseName() {
            return targetWarehouseName;
        }

        public void setTargetWarehouseName(String targetWarehouseName) {
            this.targetWarehouseName = targetWarehouseName;
        }

        public String getBatchStatus() {
            return batchStatus;
        }

        public void setBatchStatus(String batchStatus) {
            this.batchStatus = batchStatus;
        }

        public String getStatusScope() {
            return statusScope;
        }

        public void setStatusScope(String statusScope) {
            this.statusScope = statusScope;
        }

        public String getSkuKeyword() {
            return skuKeyword;
        }

        public void setSkuKeyword(String skuKeyword) {
            this.skuKeyword = skuKeyword;
        }

        public LocalDate getEtaFrom() {
            return etaFrom;
        }

        public void setEtaFrom(LocalDate etaFrom) {
            this.etaFrom = etaFrom;
        }

        public LocalDate getEtaTo() {
            return etaTo;
        }

        public void setEtaTo(LocalDate etaTo) {
            this.etaTo = etaTo;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }

        public boolean isAccessScopeRestricted() {
            return accessScopeRestricted;
        }

        public void setAccessScopeRestricted(boolean accessScopeRestricted) {
            this.accessScopeRestricted = accessScopeRestricted;
        }

        public List<InTransitStoreSiteScope> getAllowedStoreSites() {
            return allowedStoreSites;
        }

        public void setAllowedStoreSites(List<InTransitStoreSiteScope> allowedStoreSites) {
            this.allowedStoreSites = allowedStoreSites == null ? Collections.emptyList() : allowedStoreSites;
        }
    }

    public static class InTransitLineQuery {
        private Long ownerUserId;
        private Long batchId;

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public Long getBatchId() {
            return batchId;
        }

        public void setBatchId(Long batchId) {
            this.batchId = batchId;
        }
    }

    public static class SaveLineCommand {
        private Long lineId;
        private Long batchId;
        private Long packageId;
        private Long ownerUserId;
        private Long operatorUserId;
        private String boxNo;
        private String externalBoxNo;
        private String packageTrackingNo;
        private BigDecimal packageWeightKg;
        private BigDecimal packageLengthCm;
        private BigDecimal packageWidthCm;
        private BigDecimal packageHeightCm;
        private BigDecimal packageVolumeCbm;
        private BigDecimal packageVolumeWeightKg;
        private BigDecimal packageChargeableWeightKg;
        private BigDecimal measuredWeightKg;
        private BigDecimal measuredLengthCm;
        private BigDecimal measuredWidthCm;
        private BigDecimal measuredHeightCm;
        private BigDecimal measuredVolumeCbm;
        private String packageStatus;
        private String logisticsStatus;
        private boolean packageSnapshotAuthoritative;
        private String sku;
        private String msku;
        private String psku;
        private String productName;
        private String storeCode;
        private String siteCode;
        private Integer shippedQuantity;
        private Integer receivedQuantity;
        private Integer cartonCount;
        private Integer unitsPerCarton;
        private BigDecimal cartonWeightKg;
        private BigDecimal cartonVolumeCbm;

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getPackageId() { return packageId; }
        public void setPackageId(Long packageId) { this.packageId = packageId; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getBoxNo() { return boxNo; }
        public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
        public String getExternalBoxNo() { return externalBoxNo; }
        public void setExternalBoxNo(String externalBoxNo) { this.externalBoxNo = externalBoxNo; }
        public String getPackageTrackingNo() { return packageTrackingNo; }
        public void setPackageTrackingNo(String packageTrackingNo) { this.packageTrackingNo = packageTrackingNo; }
        public BigDecimal getPackageWeightKg() { return packageWeightKg; }
        public void setPackageWeightKg(BigDecimal packageWeightKg) { this.packageWeightKg = packageWeightKg; }
        public BigDecimal getPackageLengthCm() { return packageLengthCm; }
        public void setPackageLengthCm(BigDecimal packageLengthCm) { this.packageLengthCm = packageLengthCm; }
        public BigDecimal getPackageWidthCm() { return packageWidthCm; }
        public void setPackageWidthCm(BigDecimal packageWidthCm) { this.packageWidthCm = packageWidthCm; }
        public BigDecimal getPackageHeightCm() { return packageHeightCm; }
        public void setPackageHeightCm(BigDecimal packageHeightCm) { this.packageHeightCm = packageHeightCm; }
        public BigDecimal getPackageVolumeCbm() { return packageVolumeCbm; }
        public void setPackageVolumeCbm(BigDecimal packageVolumeCbm) { this.packageVolumeCbm = packageVolumeCbm; }
        public BigDecimal getPackageVolumeWeightKg() { return packageVolumeWeightKg; }
        public void setPackageVolumeWeightKg(BigDecimal packageVolumeWeightKg) { this.packageVolumeWeightKg = packageVolumeWeightKg; }
        public BigDecimal getPackageChargeableWeightKg() { return packageChargeableWeightKg; }
        public void setPackageChargeableWeightKg(BigDecimal packageChargeableWeightKg) { this.packageChargeableWeightKg = packageChargeableWeightKg; }
        public BigDecimal getMeasuredWeightKg() { return measuredWeightKg; }
        public void setMeasuredWeightKg(BigDecimal measuredWeightKg) { this.measuredWeightKg = measuredWeightKg; }
        public BigDecimal getMeasuredLengthCm() { return measuredLengthCm; }
        public void setMeasuredLengthCm(BigDecimal measuredLengthCm) { this.measuredLengthCm = measuredLengthCm; }
        public BigDecimal getMeasuredWidthCm() { return measuredWidthCm; }
        public void setMeasuredWidthCm(BigDecimal measuredWidthCm) { this.measuredWidthCm = measuredWidthCm; }
        public BigDecimal getMeasuredHeightCm() { return measuredHeightCm; }
        public void setMeasuredHeightCm(BigDecimal measuredHeightCm) { this.measuredHeightCm = measuredHeightCm; }
        public BigDecimal getMeasuredVolumeCbm() { return measuredVolumeCbm; }
        public void setMeasuredVolumeCbm(BigDecimal measuredVolumeCbm) { this.measuredVolumeCbm = measuredVolumeCbm; }
        public String getPackageStatus() { return packageStatus; }
        public void setPackageStatus(String packageStatus) { this.packageStatus = packageStatus; }
        public String getLogisticsStatus() { return logisticsStatus; }
        public void setLogisticsStatus(String logisticsStatus) { this.logisticsStatus = logisticsStatus; }
        public boolean isPackageSnapshotAuthoritative() { return packageSnapshotAuthoritative; }
        public void setPackageSnapshotAuthoritative(boolean packageSnapshotAuthoritative) {
            this.packageSnapshotAuthoritative = packageSnapshotAuthoritative;
        }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getMsku() { return msku; }
        public void setMsku(String msku) { this.msku = msku; }
        public String getPsku() { return psku; }
        public void setPsku(String psku) { this.psku = psku; }
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
    }

    public static class DeleteLineCommand {
        private Long lineId;
        private Long batchId;
        private Long ownerUserId;
        private Long operatorUserId;

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    }

    public static class SaveNodeCommand {
        private Long nodeId;
        private Long batchId;
        private Long ownerUserId;
        private Long operatorUserId;
        private String nodeStatus;
        private LocalDateTime nodeHappenedAt;
        private String description;
        private String operatorName;

        public Long getNodeId() { return nodeId; }
        public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getNodeStatus() { return nodeStatus; }
        public void setNodeStatus(String nodeStatus) { this.nodeStatus = nodeStatus; }
        public LocalDateTime getNodeHappenedAt() { return nodeHappenedAt; }
        public void setNodeHappenedAt(LocalDateTime nodeHappenedAt) { this.nodeHappenedAt = nodeHappenedAt; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getOperatorName() { return operatorName; }
        public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    }

    public static class PreviewImportCommand {
        private Long ownerUserId;
        private Long operatorUserId;
        private String fileName;
        private String contentType;
        private byte[] content;
        private BusinessAccessContext accessContext;

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public byte[] getContent() { return content; }
        public void setContent(byte[] content) { this.content = content; }
        public BusinessAccessContext getAccessContext() { return accessContext; }
        public void setAccessContext(BusinessAccessContext accessContext) { this.accessContext = accessContext; }
    }

    public static class ConfirmImportCommand {
        private Long importBatchId;
        private Long ownerUserId;
        private Long operatorUserId;
        private BusinessAccessContext accessContext;

        public Long getImportBatchId() { return importBatchId; }
        public void setImportBatchId(Long importBatchId) { this.importBatchId = importBatchId; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public BusinessAccessContext getAccessContext() { return accessContext; }
        public void setAccessContext(BusinessAccessContext accessContext) { this.accessContext = accessContext; }
    }

    public static class SaveBatchCommand {
        private Long batchId;
        private Long ownerUserId;
        private Long operatorUserId;
        private Long standardForwarderId;
        private String rawForwarderName;
        private String transportMode;
        private String targetStoreCode;
        private String targetSiteCode;
        private String targetWarehouseName;
        private LocalDate departureDate;
        private LocalDate etaDate;
        private String trackingNo;
        private String containerNo;
        private String batchReferenceNo;
        private String externalShipmentNo;
        private LocalDateTime sourceCreatedAt;
        private LocalDateTime estimatedDepartureAt;
        private LocalDateTime estimatedArrivalAt;
        private String deliveryAppointmentText;
        private String batchStatus;

        public Long getBatchId() {
            return batchId;
        }

        public void setBatchId(Long batchId) {
            this.batchId = batchId;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public Long getOperatorUserId() {
            return operatorUserId;
        }

        public void setOperatorUserId(Long operatorUserId) {
            this.operatorUserId = operatorUserId;
        }

        public Long getStandardForwarderId() {
            return standardForwarderId;
        }

        public void setStandardForwarderId(Long standardForwarderId) {
            this.standardForwarderId = standardForwarderId;
        }

        public String getRawForwarderName() {
            return rawForwarderName;
        }

        public void setRawForwarderName(String rawForwarderName) {
            this.rawForwarderName = rawForwarderName;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode;
        }

        public String getTargetStoreCode() {
            return targetStoreCode;
        }

        public void setTargetStoreCode(String targetStoreCode) {
            this.targetStoreCode = targetStoreCode;
        }

        public String getTargetSiteCode() {
            return targetSiteCode;
        }

        public void setTargetSiteCode(String targetSiteCode) {
            this.targetSiteCode = targetSiteCode;
        }

        public String getTargetWarehouseName() {
            return targetWarehouseName;
        }

        public void setTargetWarehouseName(String targetWarehouseName) {
            this.targetWarehouseName = targetWarehouseName;
        }

        public LocalDate getDepartureDate() {
            return departureDate;
        }

        public void setDepartureDate(LocalDate departureDate) {
            this.departureDate = departureDate;
        }

        public LocalDate getEtaDate() {
            return etaDate;
        }

        public void setEtaDate(LocalDate etaDate) {
            this.etaDate = etaDate;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public void setTrackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
        }

        public String getContainerNo() {
            return containerNo;
        }

        public void setContainerNo(String containerNo) {
            this.containerNo = containerNo;
        }

        public String getBatchReferenceNo() {
            return batchReferenceNo;
        }

        public void setBatchReferenceNo(String batchReferenceNo) {
            this.batchReferenceNo = batchReferenceNo;
        }

        public String getExternalShipmentNo() {
            return externalShipmentNo;
        }

        public void setExternalShipmentNo(String externalShipmentNo) {
            this.externalShipmentNo = externalShipmentNo;
        }

        public LocalDateTime getSourceCreatedAt() {
            return sourceCreatedAt;
        }

        public void setSourceCreatedAt(LocalDateTime sourceCreatedAt) {
            this.sourceCreatedAt = sourceCreatedAt;
        }

        public LocalDateTime getEstimatedDepartureAt() {
            return estimatedDepartureAt;
        }

        public void setEstimatedDepartureAt(LocalDateTime estimatedDepartureAt) {
            this.estimatedDepartureAt = estimatedDepartureAt;
        }

        public LocalDateTime getEstimatedArrivalAt() {
            return estimatedArrivalAt;
        }

        public void setEstimatedArrivalAt(LocalDateTime estimatedArrivalAt) {
            this.estimatedArrivalAt = estimatedArrivalAt;
        }

        public String getDeliveryAppointmentText() {
            return deliveryAppointmentText;
        }

        public void setDeliveryAppointmentText(String deliveryAppointmentText) {
            this.deliveryAppointmentText = deliveryAppointmentText;
        }

        public String getBatchStatus() {
            return batchStatus;
        }

        public void setBatchStatus(String batchStatus) {
            this.batchStatus = batchStatus;
        }

    }
}
