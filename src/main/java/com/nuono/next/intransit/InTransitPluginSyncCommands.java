package com.nuono.next.intransit;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public final class InTransitPluginSyncCommands {

    private InTransitPluginSyncCommands() {
    }

    public static class PluginSyncCommand {
        private Long ownerUserId;
        private Long operatorUserId;
        private BusinessAccessContext accessContext;
        private String sourceSystem;
        private String forwarderName;
        private List<PluginSyncSourceBatchExpectation> sourceBatchExpectations = Collections.emptyList();
        private List<PluginSyncBatch> batches = Collections.emptyList();

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public BusinessAccessContext getAccessContext() { return accessContext; }
        public void setAccessContext(BusinessAccessContext accessContext) { this.accessContext = accessContext; }
        public String getSourceSystem() { return sourceSystem; }
        public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
        public String getForwarderName() { return forwarderName; }
        public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
        public List<PluginSyncSourceBatchExpectation> getSourceBatchExpectations() { return sourceBatchExpectations; }
        public void setSourceBatchExpectations(List<PluginSyncSourceBatchExpectation> sourceBatchExpectations) {
            this.sourceBatchExpectations = sourceBatchExpectations == null ? Collections.emptyList() : sourceBatchExpectations;
        }
        public List<PluginSyncBatch> getBatches() { return batches; }
        public void setBatches(List<PluginSyncBatch> batches) {
            this.batches = batches == null ? Collections.emptyList() : batches;
        }
    }

    public static class EtBoxSyncPlanCommand {
        private Long ownerUserId;
        private Long operatorUserId;
        private BusinessAccessContext accessContext;
        private String sourceSystem;
        private String forwarderName;
        private boolean forceFullSync;
        private List<EtBoxSyncPlanOrder> shipOrders = Collections.emptyList();

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public BusinessAccessContext getAccessContext() { return accessContext; }
        public void setAccessContext(BusinessAccessContext accessContext) { this.accessContext = accessContext; }
        public String getSourceSystem() { return sourceSystem; }
        public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
        public String getForwarderName() { return forwarderName; }
        public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
        public boolean isForceFullSync() { return forceFullSync; }
        public void setForceFullSync(boolean forceFullSync) { this.forceFullSync = forceFullSync; }
        public List<EtBoxSyncPlanOrder> getShipOrders() { return shipOrders; }
        public void setShipOrders(List<EtBoxSyncPlanOrder> shipOrders) {
            this.shipOrders = shipOrders == null ? Collections.emptyList() : shipOrders;
        }
    }

    public static class EtBoxSyncPlanOrder {
        private String shipOrderId;
        private String waybillNo;
        private String externalShipmentNo;
        private String sourceStatus;
        private String sourceUpdatedAt;
        private List<EtBoxSyncPlanOrderBox> boxes = Collections.emptyList();

        public String getShipOrderId() { return shipOrderId; }
        public void setShipOrderId(String shipOrderId) { this.shipOrderId = shipOrderId; }
        public String getWaybillNo() { return waybillNo; }
        public void setWaybillNo(String waybillNo) { this.waybillNo = waybillNo; }
        public String getExternalShipmentNo() { return externalShipmentNo; }
        public void setExternalShipmentNo(String externalShipmentNo) { this.externalShipmentNo = externalShipmentNo; }
        public String getSourceStatus() { return sourceStatus; }
        public void setSourceStatus(String sourceStatus) { this.sourceStatus = sourceStatus; }
        public String getSourceUpdatedAt() { return sourceUpdatedAt; }
        public void setSourceUpdatedAt(String sourceUpdatedAt) { this.sourceUpdatedAt = sourceUpdatedAt; }
        public List<EtBoxSyncPlanOrderBox> getBoxes() { return boxes; }
        public void setBoxes(List<EtBoxSyncPlanOrderBox> boxes) {
            this.boxes = boxes == null ? Collections.emptyList() : boxes;
        }
    }

    public static class EtBoxSyncPlanOrderBox {
        private String boxId;
        private String clientBoxId;

        public String getBoxId() { return boxId; }
        public void setBoxId(String boxId) { this.boxId = boxId; }
        public String getClientBoxId() { return clientBoxId; }
        public void setClientBoxId(String clientBoxId) { this.clientBoxId = clientBoxId; }
    }

    public static class PluginSyncSourceBatchExpectation {
        private String batchNo;
        private Integer boxNum;
        private Integer totalQuantity;

        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public Integer getBoxNum() { return boxNum; }
        public void setBoxNum(Integer boxNum) { this.boxNum = boxNum; }
        public Integer getTotalQuantity() { return totalQuantity; }
        public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }
    }

    public static class PluginSyncBatch {
        private String batchNo;
        private String batchStatus;
        private String sourceStatus;
        private String rawStatus;
        private String status;
        private String transportMode;
        private String destination;
        private String targetWarehouseName;
        private LocalDate departureDate;
        private LocalDate officialEtaDate;
        private String trackingNo;
        private String containerNo;
        private String externalShipmentNo;
        private String sourceCreatedAt;
        private String estimatedDepartureAt;
        private String estimatedArrivalAt;
        private String deliveryAppointmentText;
        private List<PluginSyncPackage> packages = Collections.emptyList();
        private List<PluginSyncNode> nodes = Collections.emptyList();

        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public String getBatchStatus() { return batchStatus; }
        public void setBatchStatus(String batchStatus) { this.batchStatus = batchStatus; }
        public String getSourceStatus() { return sourceStatus; }
        public void setSourceStatus(String sourceStatus) { this.sourceStatus = sourceStatus; }
        public String getRawStatus() { return rawStatus; }
        public void setRawStatus(String rawStatus) { this.rawStatus = rawStatus; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getDestination() { return destination; }
        public void setDestination(String destination) { this.destination = destination; }
        public String getTargetWarehouseName() { return targetWarehouseName; }
        public void setTargetWarehouseName(String targetWarehouseName) { this.targetWarehouseName = targetWarehouseName; }
        public LocalDate getDepartureDate() { return departureDate; }
        public void setDepartureDate(LocalDate departureDate) { this.departureDate = departureDate; }
        public LocalDate getOfficialEtaDate() { return officialEtaDate; }
        public void setOfficialEtaDate(LocalDate officialEtaDate) { this.officialEtaDate = officialEtaDate; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getContainerNo() { return containerNo; }
        public void setContainerNo(String containerNo) { this.containerNo = containerNo; }
        public String getExternalShipmentNo() { return externalShipmentNo; }
        public void setExternalShipmentNo(String externalShipmentNo) { this.externalShipmentNo = externalShipmentNo; }
        public String getSourceCreatedAt() { return sourceCreatedAt; }
        public void setSourceCreatedAt(String sourceCreatedAt) { this.sourceCreatedAt = sourceCreatedAt; }
        public String getEstimatedDepartureAt() { return estimatedDepartureAt; }
        public void setEstimatedDepartureAt(String estimatedDepartureAt) { this.estimatedDepartureAt = estimatedDepartureAt; }
        public String getEstimatedArrivalAt() { return estimatedArrivalAt; }
        public void setEstimatedArrivalAt(String estimatedArrivalAt) { this.estimatedArrivalAt = estimatedArrivalAt; }
        public String getDeliveryAppointmentText() { return deliveryAppointmentText; }
        public void setDeliveryAppointmentText(String deliveryAppointmentText) { this.deliveryAppointmentText = deliveryAppointmentText; }
        public List<PluginSyncPackage> getPackages() { return packages; }
        public void setPackages(List<PluginSyncPackage> packages) {
            this.packages = packages == null ? Collections.emptyList() : packages;
        }
        public List<PluginSyncNode> getNodes() { return nodes; }
        public void setNodes(List<PluginSyncNode> nodes) {
            this.nodes = nodes == null ? Collections.emptyList() : nodes;
        }
    }

    public static class PluginSyncPackage {
        private String boxNo;
        private String externalBoxNo;
        private String trackingNo;
        private BigDecimal lengthCm;
        private BigDecimal widthCm;
        private BigDecimal heightCm;
        private BigDecimal weightKg;
        private BigDecimal volumeCbm;
        private BigDecimal volumeWeightKg;
        private BigDecimal chargeableWeightKg;
        private BigDecimal measuredWeightKg;
        private BigDecimal measuredLengthCm;
        private BigDecimal measuredWidthCm;
        private BigDecimal measuredHeightCm;
        private BigDecimal measuredVolumeCbm;
        private String packageStatus;
        private String logisticsStatus;
        private List<PluginSyncLine> lines = Collections.emptyList();

        public String getBoxNo() { return boxNo; }
        public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
        public String getExternalBoxNo() { return externalBoxNo; }
        public void setExternalBoxNo(String externalBoxNo) { this.externalBoxNo = externalBoxNo; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public BigDecimal getLengthCm() { return lengthCm; }
        public void setLengthCm(BigDecimal lengthCm) { this.lengthCm = lengthCm; }
        public BigDecimal getWidthCm() { return widthCm; }
        public void setWidthCm(BigDecimal widthCm) { this.widthCm = widthCm; }
        public BigDecimal getHeightCm() { return heightCm; }
        public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
        public BigDecimal getWeightKg() { return weightKg; }
        public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
        public BigDecimal getVolumeCbm() { return volumeCbm; }
        public void setVolumeCbm(BigDecimal volumeCbm) { this.volumeCbm = volumeCbm; }
        public BigDecimal getVolumeWeightKg() { return volumeWeightKg; }
        public void setVolumeWeightKg(BigDecimal volumeWeightKg) { this.volumeWeightKg = volumeWeightKg; }
        public BigDecimal getChargeableWeightKg() { return chargeableWeightKg; }
        public void setChargeableWeightKg(BigDecimal chargeableWeightKg) { this.chargeableWeightKg = chargeableWeightKg; }
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
        public List<PluginSyncLine> getLines() { return lines; }
        public void setLines(List<PluginSyncLine> lines) {
            this.lines = lines == null ? Collections.emptyList() : lines;
        }
    }

    public static class PluginSyncLine {
        private String barcode;
        private String psku;
        private String sku;
        private String msku;
        private String productName;
        private String storeCode;
        private String siteCode;
        private Integer shippedQuantity;
        private Integer receivedQuantity;
        private Integer cartonCount;
        private Integer unitsPerCarton;
        private BigDecimal cartonWeightKg;
        private BigDecimal cartonVolumeCbm;

        public String getBarcode() { return barcode; }
        public void setBarcode(String barcode) { this.barcode = barcode; }
        public String getPsku() { return psku; }
        public void setPsku(String psku) { this.psku = psku; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getMsku() { return msku; }
        public void setMsku(String msku) { this.msku = msku; }
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

    public static class PluginSyncNode {
        private String nodeStatus;
        private String nodeTime;
        private String description;
        private String operatorName;

        public String getNodeStatus() { return nodeStatus; }
        public void setNodeStatus(String nodeStatus) { this.nodeStatus = nodeStatus; }
        public String getNodeTime() { return nodeTime; }
        public void setNodeTime(String nodeTime) { this.nodeTime = nodeTime; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getOperatorName() { return operatorName; }
        public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    }
}
