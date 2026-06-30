package com.nuono.next.intransit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nuono.next.product.ProductImageUrlSupport;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class InTransitBatchRecords {

    private InTransitBatchRecords() {
    }

    public static class BatchRow {
        private Long id;
        private Long ownerUserId;
        private Long standardForwarderId;
        private String standardForwarderCode;
        private String standardForwarderName;
        private String rawForwarderName;
        private String normalizedRawForwarderName;
        private String forwarderQualityStatus;
        private String transportMode;
        private String batchStatus;
        private String targetStoreCode;
        private String targetSiteCode;
        private String targetWarehouseName;
        private LocalDate departureDate;
        private LocalDate etaDate;
        private LocalDateTime createdAt;
        private LocalDateTime domesticReceivedAt;
        private String trackingNo;
        private String containerNo;
        private String batchReferenceNo;
        private String externalShipmentNo;
        private LocalDateTime sourceCreatedAt;
        private LocalDateTime estimatedDepartureAt;
        private LocalDateTime estimatedArrivalAt;
        private String deliveryAppointmentText;
        private String missingFieldsJson;
        private Integer boxCount;
        private Integer skuCount;
        private Integer shippedQuantityTotal;
        private Integer receivedQuantityTotal;
        private Integer remainingQuantityTotal;
        private Integer cartonCountTotal;
        private BigDecimal totalWeightKg;
        private BigDecimal totalVolumeCbm;
        private String latestNodeStatus;
        private LocalDateTime latestNodeHappenedAt;
        private String latestNodeDescription;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getStandardForwarderId() { return standardForwarderId; }
        public void setStandardForwarderId(Long standardForwarderId) { this.standardForwarderId = standardForwarderId; }
        public String getStandardForwarderCode() { return standardForwarderCode; }
        public void setStandardForwarderCode(String standardForwarderCode) { this.standardForwarderCode = standardForwarderCode; }
        public String getStandardForwarderName() { return standardForwarderName; }
        public void setStandardForwarderName(String standardForwarderName) { this.standardForwarderName = standardForwarderName; }
        public String getRawForwarderName() { return rawForwarderName; }
        public void setRawForwarderName(String rawForwarderName) { this.rawForwarderName = rawForwarderName; }
        public String getNormalizedRawForwarderName() { return normalizedRawForwarderName; }
        public void setNormalizedRawForwarderName(String normalizedRawForwarderName) { this.normalizedRawForwarderName = normalizedRawForwarderName; }
        public String getForwarderQualityStatus() { return forwarderQualityStatus; }
        public void setForwarderQualityStatus(String forwarderQualityStatus) { this.forwarderQualityStatus = forwarderQualityStatus; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getBatchStatus() { return batchStatus; }
        public void setBatchStatus(String batchStatus) { this.batchStatus = batchStatus; }
        public String getTargetStoreCode() { return targetStoreCode; }
        public void setTargetStoreCode(String targetStoreCode) { this.targetStoreCode = targetStoreCode; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public String getTargetWarehouseName() { return targetWarehouseName; }
        public void setTargetWarehouseName(String targetWarehouseName) { this.targetWarehouseName = targetWarehouseName; }
        public LocalDate getDepartureDate() { return departureDate; }
        public void setDepartureDate(LocalDate departureDate) { this.departureDate = departureDate; }
        public LocalDate getEtaDate() { return etaDate; }
        public void setEtaDate(LocalDate etaDate) { this.etaDate = etaDate; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getDomesticReceivedAt() { return domesticReceivedAt; }
        public void setDomesticReceivedAt(LocalDateTime domesticReceivedAt) { this.domesticReceivedAt = domesticReceivedAt; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getContainerNo() { return containerNo; }
        public void setContainerNo(String containerNo) { this.containerNo = containerNo; }
        public String getBatchReferenceNo() { return batchReferenceNo; }
        public void setBatchReferenceNo(String batchReferenceNo) { this.batchReferenceNo = batchReferenceNo; }
        public String getExternalShipmentNo() { return externalShipmentNo; }
        public void setExternalShipmentNo(String externalShipmentNo) { this.externalShipmentNo = externalShipmentNo; }
        public LocalDateTime getSourceCreatedAt() { return sourceCreatedAt; }
        public void setSourceCreatedAt(LocalDateTime sourceCreatedAt) { this.sourceCreatedAt = sourceCreatedAt; }
        public LocalDateTime getEstimatedDepartureAt() { return estimatedDepartureAt; }
        public void setEstimatedDepartureAt(LocalDateTime estimatedDepartureAt) { this.estimatedDepartureAt = estimatedDepartureAt; }
        public LocalDateTime getEstimatedArrivalAt() { return estimatedArrivalAt; }
        public void setEstimatedArrivalAt(LocalDateTime estimatedArrivalAt) { this.estimatedArrivalAt = estimatedArrivalAt; }
        public String getDeliveryAppointmentText() { return deliveryAppointmentText; }
        public void setDeliveryAppointmentText(String deliveryAppointmentText) { this.deliveryAppointmentText = deliveryAppointmentText; }
        public String getMissingFieldsJson() { return missingFieldsJson; }
        public void setMissingFieldsJson(String missingFieldsJson) { this.missingFieldsJson = missingFieldsJson; }
        public Integer getBoxCount() { return boxCount; }
        public void setBoxCount(Integer boxCount) { this.boxCount = boxCount; }
        public Integer getSkuCount() { return skuCount; }
        public void setSkuCount(Integer skuCount) { this.skuCount = skuCount; }
        public Integer getShippedQuantityTotal() { return shippedQuantityTotal; }
        public void setShippedQuantityTotal(Integer shippedQuantityTotal) { this.shippedQuantityTotal = shippedQuantityTotal; }
        public Integer getReceivedQuantityTotal() { return receivedQuantityTotal; }
        public void setReceivedQuantityTotal(Integer receivedQuantityTotal) { this.receivedQuantityTotal = receivedQuantityTotal; }
        public Integer getRemainingQuantityTotal() { return remainingQuantityTotal; }
        public void setRemainingQuantityTotal(Integer remainingQuantityTotal) { this.remainingQuantityTotal = remainingQuantityTotal; }
        public Integer getCartonCountTotal() { return cartonCountTotal; }
        public void setCartonCountTotal(Integer cartonCountTotal) { this.cartonCountTotal = cartonCountTotal; }
        public BigDecimal getTotalWeightKg() { return totalWeightKg; }
        public void setTotalWeightKg(BigDecimal totalWeightKg) { this.totalWeightKg = totalWeightKg; }
        public BigDecimal getTotalVolumeCbm() { return totalVolumeCbm; }
        public void setTotalVolumeCbm(BigDecimal totalVolumeCbm) { this.totalVolumeCbm = totalVolumeCbm; }
        public String getLatestNodeStatus() { return latestNodeStatus; }
        public void setLatestNodeStatus(String latestNodeStatus) { this.latestNodeStatus = latestNodeStatus; }
        public LocalDateTime getLatestNodeHappenedAt() { return latestNodeHappenedAt; }
        public void setLatestNodeHappenedAt(LocalDateTime latestNodeHappenedAt) { this.latestNodeHappenedAt = latestNodeHappenedAt; }
        public String getLatestNodeDescription() { return latestNodeDescription; }
        public void setLatestNodeDescription(String latestNodeDescription) { this.latestNodeDescription = latestNodeDescription; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class BatchView {
        private Long batchId;
        private Long standardForwarderId;
        private String standardForwarderCode;
        private String standardForwarderName;
        private String rawForwarderName;
        private String normalizedRawForwarderName;
        private String forwarderQualityStatus;
        private String transportMode;
        private String batchStatus;
        private String targetStoreCode;
        private String targetSiteCode;
        private String targetWarehouseName;
        private LocalDate departureDate;
        private LocalDate etaDate;
        private LocalDateTime createdAt;
        private LocalDateTime domesticReceivedAt;
        private String trackingNo;
        private String containerNo;
        private String batchReferenceNo;
        private String externalShipmentNo;
        private LocalDateTime sourceCreatedAt;
        private LocalDateTime estimatedDepartureAt;
        private LocalDateTime estimatedArrivalAt;
        private String deliveryAppointmentText;
        private List<String> missingFields = Collections.emptyList();
        private Integer boxCount;
        private Integer skuCount;
        private Integer shippedQuantityTotal;
        private Integer receivedQuantityTotal;
        private Integer remainingQuantityTotal;
        private Integer cartonCountTotal;
        private BigDecimal totalWeightKg;
        private BigDecimal totalVolumeCbm;
        private String latestNodeStatus;
        private LocalDateTime latestNodeHappenedAt;
        private String latestNodeDescription;

        public static BatchView from(BatchRow row) {
            BatchView view = new BatchView();
            view.setBatchId(row.getId());
            view.setStandardForwarderId(row.getStandardForwarderId());
            view.setStandardForwarderCode(row.getStandardForwarderCode());
            view.setStandardForwarderName(row.getStandardForwarderName());
            view.setRawForwarderName(row.getRawForwarderName());
            view.setNormalizedRawForwarderName(row.getNormalizedRawForwarderName());
            view.setForwarderQualityStatus(row.getForwarderQualityStatus());
            view.setTransportMode(row.getTransportMode());
            view.setBatchStatus(row.getBatchStatus());
            view.setTargetStoreCode(row.getTargetStoreCode());
            view.setTargetSiteCode(row.getTargetSiteCode());
            view.setTargetWarehouseName(row.getTargetWarehouseName());
            view.setDepartureDate(row.getDepartureDate());
            view.setEtaDate(row.getEtaDate());
            view.setCreatedAt(row.getCreatedAt());
            view.setDomesticReceivedAt(row.getDomesticReceivedAt());
            view.setTrackingNo(row.getTrackingNo());
            view.setContainerNo(row.getContainerNo());
            view.setBatchReferenceNo(row.getBatchReferenceNo());
            view.setExternalShipmentNo(row.getExternalShipmentNo());
            view.setSourceCreatedAt(row.getSourceCreatedAt());
            view.setEstimatedDepartureAt(row.getEstimatedDepartureAt());
            view.setEstimatedArrivalAt(row.getEstimatedArrivalAt());
            view.setDeliveryAppointmentText(row.getDeliveryAppointmentText());
            view.setMissingFields(parseMissingFields(row.getMissingFieldsJson()));
            view.setBoxCount(row.getBoxCount());
            view.setSkuCount(row.getSkuCount());
            view.setShippedQuantityTotal(row.getShippedQuantityTotal());
            view.setReceivedQuantityTotal(row.getReceivedQuantityTotal());
            view.setRemainingQuantityTotal(row.getRemainingQuantityTotal());
            view.setCartonCountTotal(row.getCartonCountTotal());
            view.setTotalWeightKg(row.getTotalWeightKg());
            view.setTotalVolumeCbm(row.getTotalVolumeCbm());
            view.setLatestNodeStatus(row.getLatestNodeStatus());
            view.setLatestNodeHappenedAt(row.getLatestNodeHappenedAt());
            view.setLatestNodeDescription(row.getLatestNodeDescription());
            return view;
        }

        @JsonIgnore
        public List<String> getFieldNames() {
            List<String> names = new ArrayList<>();
            for (Method method : getClass().getMethods()) {
                if (method.getParameterCount() == 0 && method.getName().startsWith("get") && !method.getName().equals("getClass")) {
                    String suffix = method.getName().substring(3);
                    names.add(Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1));
                }
            }
            return names;
        }

        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getStandardForwarderId() { return standardForwarderId; }
        public void setStandardForwarderId(Long standardForwarderId) { this.standardForwarderId = standardForwarderId; }
        public String getStandardForwarderCode() { return standardForwarderCode; }
        public void setStandardForwarderCode(String standardForwarderCode) { this.standardForwarderCode = standardForwarderCode; }
        public String getStandardForwarderName() { return standardForwarderName; }
        public void setStandardForwarderName(String standardForwarderName) { this.standardForwarderName = standardForwarderName; }
        public String getRawForwarderName() { return rawForwarderName; }
        public void setRawForwarderName(String rawForwarderName) { this.rawForwarderName = rawForwarderName; }
        public String getNormalizedRawForwarderName() { return normalizedRawForwarderName; }
        public void setNormalizedRawForwarderName(String normalizedRawForwarderName) { this.normalizedRawForwarderName = normalizedRawForwarderName; }
        public String getForwarderQualityStatus() { return forwarderQualityStatus; }
        public void setForwarderQualityStatus(String forwarderQualityStatus) { this.forwarderQualityStatus = forwarderQualityStatus; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getBatchStatus() { return batchStatus; }
        public void setBatchStatus(String batchStatus) { this.batchStatus = batchStatus; }
        public String getTargetStoreCode() { return targetStoreCode; }
        public void setTargetStoreCode(String targetStoreCode) { this.targetStoreCode = targetStoreCode; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public String getTargetWarehouseName() { return targetWarehouseName; }
        public void setTargetWarehouseName(String targetWarehouseName) { this.targetWarehouseName = targetWarehouseName; }
        public LocalDate getDepartureDate() { return departureDate; }
        public void setDepartureDate(LocalDate departureDate) { this.departureDate = departureDate; }
        public LocalDate getEtaDate() { return etaDate; }
        public void setEtaDate(LocalDate etaDate) { this.etaDate = etaDate; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getDomesticReceivedAt() { return domesticReceivedAt; }
        public void setDomesticReceivedAt(LocalDateTime domesticReceivedAt) { this.domesticReceivedAt = domesticReceivedAt; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getContainerNo() { return containerNo; }
        public void setContainerNo(String containerNo) { this.containerNo = containerNo; }
        public String getBatchReferenceNo() { return batchReferenceNo; }
        public void setBatchReferenceNo(String batchReferenceNo) { this.batchReferenceNo = batchReferenceNo; }
        public String getExternalShipmentNo() { return externalShipmentNo; }
        public void setExternalShipmentNo(String externalShipmentNo) { this.externalShipmentNo = externalShipmentNo; }
        public LocalDateTime getSourceCreatedAt() { return sourceCreatedAt; }
        public void setSourceCreatedAt(LocalDateTime sourceCreatedAt) { this.sourceCreatedAt = sourceCreatedAt; }
        public LocalDateTime getEstimatedDepartureAt() { return estimatedDepartureAt; }
        public void setEstimatedDepartureAt(LocalDateTime estimatedDepartureAt) { this.estimatedDepartureAt = estimatedDepartureAt; }
        public LocalDateTime getEstimatedArrivalAt() { return estimatedArrivalAt; }
        public void setEstimatedArrivalAt(LocalDateTime estimatedArrivalAt) { this.estimatedArrivalAt = estimatedArrivalAt; }
        public String getDeliveryAppointmentText() { return deliveryAppointmentText; }
        public void setDeliveryAppointmentText(String deliveryAppointmentText) { this.deliveryAppointmentText = deliveryAppointmentText; }
        public List<String> getMissingFields() { return missingFields; }
        public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }
        public Integer getBoxCount() { return boxCount; }
        public void setBoxCount(Integer boxCount) { this.boxCount = boxCount; }
        public Integer getSkuCount() { return skuCount; }
        public void setSkuCount(Integer skuCount) { this.skuCount = skuCount; }
        public Integer getShippedQuantityTotal() { return shippedQuantityTotal; }
        public void setShippedQuantityTotal(Integer shippedQuantityTotal) { this.shippedQuantityTotal = shippedQuantityTotal; }
        public Integer getReceivedQuantityTotal() { return receivedQuantityTotal; }
        public void setReceivedQuantityTotal(Integer receivedQuantityTotal) { this.receivedQuantityTotal = receivedQuantityTotal; }
        public Integer getRemainingQuantityTotal() { return remainingQuantityTotal; }
        public void setRemainingQuantityTotal(Integer remainingQuantityTotal) { this.remainingQuantityTotal = remainingQuantityTotal; }
        public Integer getCartonCountTotal() { return cartonCountTotal; }
        public void setCartonCountTotal(Integer cartonCountTotal) { this.cartonCountTotal = cartonCountTotal; }
        public BigDecimal getTotalWeightKg() { return totalWeightKg; }
        public void setTotalWeightKg(BigDecimal totalWeightKg) { this.totalWeightKg = totalWeightKg; }
        public BigDecimal getTotalVolumeCbm() { return totalVolumeCbm; }
        public void setTotalVolumeCbm(BigDecimal totalVolumeCbm) { this.totalVolumeCbm = totalVolumeCbm; }
        public String getLatestNodeStatus() { return latestNodeStatus; }
        public void setLatestNodeStatus(String latestNodeStatus) { this.latestNodeStatus = latestNodeStatus; }
        public LocalDateTime getLatestNodeHappenedAt() { return latestNodeHappenedAt; }
        public void setLatestNodeHappenedAt(LocalDateTime latestNodeHappenedAt) { this.latestNodeHappenedAt = latestNodeHappenedAt; }
        public String getLatestNodeDescription() { return latestNodeDescription; }
        public void setLatestNodeDescription(String latestNodeDescription) { this.latestNodeDescription = latestNodeDescription; }
    }

    public static class BatchListView {
        private String mode = "local-db";
        private boolean ready = true;
        private Integer totalCount;
        private Integer page;
        private Integer pageSize;
        private List<BatchView> items = Collections.emptyList();

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public boolean isReady() { return ready; }
        public void setReady(boolean ready) { this.ready = ready; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        public Integer getPage() { return page; }
        public void setPage(Integer page) { this.page = page; }
        public Integer getPageSize() { return pageSize; }
        public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
        public List<BatchView> getItems() { return items; }
        public void setItems(List<BatchView> items) { this.items = items; }
    }

    public static class LineRow {
        private Long id;
        private Long ownerUserId;
        private Long batchId;
        private Long packageId;
        private String boxNo;
        private String sku;
        private String msku;
        private String psku;
        private String productName;
        private String storeCode;
        private String siteCode;
        private Integer shippedQuantity;
        private Integer receivedQuantity;
        private Integer remainingQuantity;
        private Integer cartonCount;
        private Integer unitsPerCarton;
        private BigDecimal cartonWeightKg;
        private BigDecimal cartonVolumeCbm;
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
        private Long matchedProductId;
        private String productSkuParent;
        private String productTitle;
        private String productImageUrl;
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
        public Integer getRemainingQuantity() { return remainingQuantity; }
        public void setRemainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; }
        public Integer getCartonCount() { return cartonCount; }
        public void setCartonCount(Integer cartonCount) { this.cartonCount = cartonCount; }
        public Integer getUnitsPerCarton() { return unitsPerCarton; }
        public void setUnitsPerCarton(Integer unitsPerCarton) { this.unitsPerCarton = unitsPerCarton; }
        public BigDecimal getCartonWeightKg() { return cartonWeightKg; }
        public void setCartonWeightKg(BigDecimal cartonWeightKg) { this.cartonWeightKg = cartonWeightKg; }
        public BigDecimal getCartonVolumeCbm() { return cartonVolumeCbm; }
        public void setCartonVolumeCbm(BigDecimal cartonVolumeCbm) { this.cartonVolumeCbm = cartonVolumeCbm; }
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
        public Long getMatchedProductId() { return matchedProductId; }
        public void setMatchedProductId(Long matchedProductId) { this.matchedProductId = matchedProductId; }
        public String getProductSkuParent() { return productSkuParent; }
        public void setProductSkuParent(String productSkuParent) { this.productSkuParent = productSkuParent; }
        public String getProductTitle() { return productTitle; }
        public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
        public String getProductImageUrl() { return productImageUrl; }
        public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class LineView {
        private Long lineId;
        private Long batchId;
        private Long packageId;
        private String boxNo;
        private String sku;
        private String msku;
        private String psku;
        private String productName;
        private String storeCode;
        private String siteCode;
        private Integer shippedQuantity;
        private Integer receivedQuantity;
        private Integer remainingQuantity;
        private Integer cartonCount;
        private Integer unitsPerCarton;
        private BigDecimal cartonWeightKg;
        private BigDecimal cartonVolumeCbm;
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
        private Long matchedProductId;
        private String productSkuParent;
        private String productTitle;
        private String productImageUrl;

        public static LineView from(LineRow row) {
            LineView view = new LineView();
            view.setLineId(row.getId());
            view.setBatchId(row.getBatchId());
            view.setPackageId(row.getPackageId());
            view.setBoxNo(row.getBoxNo());
            view.setSku(row.getSku());
            view.setMsku(row.getMsku());
            view.setPsku(row.getPsku());
            view.setProductName(row.getProductName());
            view.setStoreCode(row.getStoreCode());
            view.setSiteCode(row.getSiteCode());
            view.setShippedQuantity(row.getShippedQuantity());
            view.setReceivedQuantity(row.getReceivedQuantity());
            view.setRemainingQuantity(row.getRemainingQuantity());
            view.setCartonCount(row.getCartonCount());
            view.setUnitsPerCarton(row.getUnitsPerCarton());
            view.setCartonWeightKg(row.getCartonWeightKg());
            view.setCartonVolumeCbm(row.getCartonVolumeCbm());
            view.setExternalBoxNo(row.getExternalBoxNo());
            view.setPackageTrackingNo(row.getPackageTrackingNo());
            view.setPackageWeightKg(row.getPackageWeightKg());
            view.setPackageLengthCm(row.getPackageLengthCm());
            view.setPackageWidthCm(row.getPackageWidthCm());
            view.setPackageHeightCm(row.getPackageHeightCm());
            view.setPackageVolumeCbm(row.getPackageVolumeCbm());
            view.setPackageVolumeWeightKg(row.getPackageVolumeWeightKg());
            view.setPackageChargeableWeightKg(row.getPackageChargeableWeightKg());
            view.setMeasuredWeightKg(row.getMeasuredWeightKg());
            view.setMeasuredLengthCm(row.getMeasuredLengthCm());
            view.setMeasuredWidthCm(row.getMeasuredWidthCm());
            view.setMeasuredHeightCm(row.getMeasuredHeightCm());
            view.setMeasuredVolumeCbm(row.getMeasuredVolumeCbm());
            view.setPackageStatus(row.getPackageStatus());
            view.setLogisticsStatus(row.getLogisticsStatus());
            view.setMatchedProductId(row.getMatchedProductId());
            view.setProductSkuParent(row.getProductSkuParent());
            view.setProductTitle(row.getProductTitle());
            view.setProductImageUrl(ProductImageUrlSupport.normalize(row.getProductImageUrl()));
            return view;
        }

        public Long getLineId() { return lineId; }
        public void setLineId(Long lineId) { this.lineId = lineId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getPackageId() { return packageId; }
        public void setPackageId(Long packageId) { this.packageId = packageId; }
        public String getBoxNo() { return boxNo; }
        public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
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
        public Integer getRemainingQuantity() { return remainingQuantity; }
        public void setRemainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; }
        public Integer getCartonCount() { return cartonCount; }
        public void setCartonCount(Integer cartonCount) { this.cartonCount = cartonCount; }
        public Integer getUnitsPerCarton() { return unitsPerCarton; }
        public void setUnitsPerCarton(Integer unitsPerCarton) { this.unitsPerCarton = unitsPerCarton; }
        public BigDecimal getCartonWeightKg() { return cartonWeightKg; }
        public void setCartonWeightKg(BigDecimal cartonWeightKg) { this.cartonWeightKg = cartonWeightKg; }
        public BigDecimal getCartonVolumeCbm() { return cartonVolumeCbm; }
        public void setCartonVolumeCbm(BigDecimal cartonVolumeCbm) { this.cartonVolumeCbm = cartonVolumeCbm; }
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
        public Long getMatchedProductId() { return matchedProductId; }
        public void setMatchedProductId(Long matchedProductId) { this.matchedProductId = matchedProductId; }
        public String getProductSkuParent() { return productSkuParent; }
        public void setProductSkuParent(String productSkuParent) { this.productSkuParent = productSkuParent; }
        public String getProductTitle() { return productTitle; }
        public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
        public String getProductImageUrl() { return productImageUrl; }
        public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
    }

    public static class LineListView {
        private String mode = "local-db";
        private boolean ready = true;
        private List<LineView> items = Collections.emptyList();

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public boolean isReady() { return ready; }
        public void setReady(boolean ready) { this.ready = ready; }
        public List<LineView> getItems() { return items; }
        public void setItems(List<LineView> items) { this.items = items; }
    }

    public static class PackageRow {
        private Long id;
        private Long ownerUserId;
        private Long batchId;
        private String boxNo;
        private String externalBoxNo;
        private String trackingNo;
        private BigDecimal weightKg;
        private BigDecimal lengthCm;
        private BigDecimal widthCm;
        private BigDecimal heightCm;
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
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getBoxNo() { return boxNo; }
        public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
        public String getExternalBoxNo() { return externalBoxNo; }
        public void setExternalBoxNo(String externalBoxNo) { this.externalBoxNo = externalBoxNo; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public BigDecimal getWeightKg() { return weightKg; }
        public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
        public BigDecimal getLengthCm() { return lengthCm; }
        public void setLengthCm(BigDecimal lengthCm) { this.lengthCm = lengthCm; }
        public BigDecimal getWidthCm() { return widthCm; }
        public void setWidthCm(BigDecimal widthCm) { this.widthCm = widthCm; }
        public BigDecimal getHeightCm() { return heightCm; }
        public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
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
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class BatchAggregateRow {
        private Integer boxCount;
        private Integer skuCount;
        private Integer shippedQuantityTotal;
        private Integer receivedQuantityTotal;
        private Integer remainingQuantityTotal;
        private Integer cartonCountTotal;
        private BigDecimal totalWeightKg;
        private BigDecimal totalVolumeCbm;

        public Integer getBoxCount() { return boxCount; }
        public void setBoxCount(Integer boxCount) { this.boxCount = boxCount; }
        public Integer getSkuCount() { return skuCount; }
        public void setSkuCount(Integer skuCount) { this.skuCount = skuCount; }
        public Integer getShippedQuantityTotal() { return shippedQuantityTotal; }
        public void setShippedQuantityTotal(Integer shippedQuantityTotal) { this.shippedQuantityTotal = shippedQuantityTotal; }
        public Integer getReceivedQuantityTotal() { return receivedQuantityTotal; }
        public void setReceivedQuantityTotal(Integer receivedQuantityTotal) { this.receivedQuantityTotal = receivedQuantityTotal; }
        public Integer getRemainingQuantityTotal() { return remainingQuantityTotal; }
        public void setRemainingQuantityTotal(Integer remainingQuantityTotal) { this.remainingQuantityTotal = remainingQuantityTotal; }
        public Integer getCartonCountTotal() { return cartonCountTotal; }
        public void setCartonCountTotal(Integer cartonCountTotal) { this.cartonCountTotal = cartonCountTotal; }
        public BigDecimal getTotalWeightKg() { return totalWeightKg; }
        public void setTotalWeightKg(BigDecimal totalWeightKg) { this.totalWeightKg = totalWeightKg; }
        public BigDecimal getTotalVolumeCbm() { return totalVolumeCbm; }
        public void setTotalVolumeCbm(BigDecimal totalVolumeCbm) { this.totalVolumeCbm = totalVolumeCbm; }
    }

    public static class NodeRow {
        private Long id;
        private Long ownerUserId;
        private Long batchId;
        private String nodeStatus;
        private LocalDateTime nodeHappenedAt;
        private String description;
        private String operatorName;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getNodeStatus() { return nodeStatus; }
        public void setNodeStatus(String nodeStatus) { this.nodeStatus = nodeStatus; }
        public LocalDateTime getNodeHappenedAt() { return nodeHappenedAt; }
        public void setNodeHappenedAt(LocalDateTime nodeHappenedAt) { this.nodeHappenedAt = nodeHappenedAt; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getOperatorName() { return operatorName; }
        public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class NodeView {
        private Long nodeId;
        private Long batchId;
        private String nodeStatus;
        private LocalDateTime nodeHappenedAt;
        private String description;
        private String operatorName;

        public static NodeView from(NodeRow row) {
            NodeView view = new NodeView();
            view.setNodeId(row.getId());
            view.setBatchId(row.getBatchId());
            view.setNodeStatus(row.getNodeStatus());
            view.setNodeHappenedAt(row.getNodeHappenedAt());
            view.setDescription(row.getDescription());
            view.setOperatorName(row.getOperatorName());
            return view;
        }

        public Long getNodeId() { return nodeId; }
        public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getNodeStatus() { return nodeStatus; }
        public void setNodeStatus(String nodeStatus) { this.nodeStatus = nodeStatus; }
        public LocalDateTime getNodeHappenedAt() { return nodeHappenedAt; }
        public void setNodeHappenedAt(LocalDateTime nodeHappenedAt) { this.nodeHappenedAt = nodeHappenedAt; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getOperatorName() { return operatorName; }
        public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    }

    public static class NodeListView {
        private String mode = "local-db";
        private boolean ready = true;
        private List<NodeView> items = Collections.emptyList();

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public boolean isReady() { return ready; }
        public void setReady(boolean ready) { this.ready = ready; }
        public List<NodeView> getItems() { return items; }
        public void setItems(List<NodeView> items) { this.items = items; }
    }

    public static class BatchLatestNodeRow {
        private String latestNodeStatus;
        private LocalDateTime latestNodeHappenedAt;
        private String latestNodeDescription;
        private String derivedBatchStatus;

        public String getLatestNodeStatus() { return latestNodeStatus; }
        public void setLatestNodeStatus(String latestNodeStatus) { this.latestNodeStatus = latestNodeStatus; }
        public LocalDateTime getLatestNodeHappenedAt() { return latestNodeHappenedAt; }
        public void setLatestNodeHappenedAt(LocalDateTime latestNodeHappenedAt) { this.latestNodeHappenedAt = latestNodeHappenedAt; }
        public String getLatestNodeDescription() { return latestNodeDescription; }
        public void setLatestNodeDescription(String latestNodeDescription) { this.latestNodeDescription = latestNodeDescription; }
        public String getDerivedBatchStatus() { return derivedBatchStatus; }
        public void setDerivedBatchStatus(String derivedBatchStatus) { this.derivedBatchStatus = derivedBatchStatus; }
    }

    public static class ImportBatchRow {
        private Long id;
        private Long ownerUserId;
        private String fileName;
        private String sourceType;
        private String status;
        private Integer totalRowCount;
        private Integer validRowCount;
        private Integer errorCount;
        private Integer warningCount;
        private String summaryJson;
        private String rawPreviewJson;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getTotalRowCount() { return totalRowCount; }
        public void setTotalRowCount(Integer totalRowCount) { this.totalRowCount = totalRowCount; }
        public Integer getValidRowCount() { return validRowCount; }
        public void setValidRowCount(Integer validRowCount) { this.validRowCount = validRowCount; }
        public Integer getErrorCount() { return errorCount; }
        public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
        public Integer getWarningCount() { return warningCount; }
        public void setWarningCount(Integer warningCount) { this.warningCount = warningCount; }
        public String getSummaryJson() { return summaryJson; }
        public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
        public String getRawPreviewJson() { return rawPreviewJson; }
        public void setRawPreviewJson(String rawPreviewJson) { this.rawPreviewJson = rawPreviewJson; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class OperationAuditRow {
        private Long id;
        private Long ownerUserId;
        private Long operatorUserId;
        private String operationType;
        private String targetType;
        private Long targetId;
        private Long batchId;
        private String storeCode;
        private String siteCode;
        private String summary;
        private String detailJson;
        private Long createdBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getOperatorUserId() { return operatorUserId; }
        public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public Long getTargetId() { return targetId; }
        public void setTargetId(Long targetId) { this.targetId = targetId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getStoreCode() { return storeCode; }
        public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
        public String getSiteCode() { return siteCode; }
        public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getDetailJson() { return detailJson; }
        public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    }

    public static class ImportPreviewIssueView {
        private String level;
        private String code;
        private String message;
        private Integer rowNumber;
        private String field;

        public ImportPreviewIssueView() {
        }

        public ImportPreviewIssueView(String level, String code, String message, Integer rowNumber, String field) {
            this.level = level;
            this.code = code;
            this.message = message;
            this.rowNumber = rowNumber;
            this.field = field;
        }

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Integer getRowNumber() { return rowNumber; }
        public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
    }

    public static class ImportPreviewLineView {
        private Integer rowNumber;
        private String boxNo;
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
        private List<ImportPreviewIssueView> issues = Collections.emptyList();

        public Integer getRowNumber() { return rowNumber; }
        public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
        public String getBoxNo() { return boxNo; }
        public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
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
        public List<ImportPreviewIssueView> getIssues() { return issues; }
        public void setIssues(List<ImportPreviewIssueView> issues) { this.issues = issues == null ? Collections.emptyList() : issues; }
    }

    public static class ImportPreviewBatchView {
        private String batchKey;
        private String batchReferenceNo;
        private String batchStatus;
        private String rawForwarderName;
        private Long standardForwarderId;
        private String standardForwarderName;
        private String forwarderQualityStatus;
        private String transportMode;
        private String targetStoreCode;
        private String targetSiteCode;
        private String targetWarehouseName;
        private LocalDate departureDate;
        private LocalDate etaDate;
        private LocalDate domesticReceivedAt;
        private LocalDate outboundAt;
        private LocalDate customsReleasedAt;
        private LocalDate etWarehouseReceivedAt;
        private String trackingNo;
        private String containerNo;
        private List<ImportPreviewLineView> lines = Collections.emptyList();
        private List<ImportPreviewIssueView> issues = Collections.emptyList();

        public String getBatchKey() { return batchKey; }
        public void setBatchKey(String batchKey) { this.batchKey = batchKey; }
        public String getBatchReferenceNo() { return batchReferenceNo; }
        public void setBatchReferenceNo(String batchReferenceNo) { this.batchReferenceNo = batchReferenceNo; }
        public String getBatchStatus() { return batchStatus; }
        public void setBatchStatus(String batchStatus) { this.batchStatus = batchStatus; }
        public String getRawForwarderName() { return rawForwarderName; }
        public void setRawForwarderName(String rawForwarderName) { this.rawForwarderName = rawForwarderName; }
        public Long getStandardForwarderId() { return standardForwarderId; }
        public void setStandardForwarderId(Long standardForwarderId) { this.standardForwarderId = standardForwarderId; }
        public String getStandardForwarderName() { return standardForwarderName; }
        public void setStandardForwarderName(String standardForwarderName) { this.standardForwarderName = standardForwarderName; }
        public String getForwarderQualityStatus() { return forwarderQualityStatus; }
        public void setForwarderQualityStatus(String forwarderQualityStatus) { this.forwarderQualityStatus = forwarderQualityStatus; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getTargetStoreCode() { return targetStoreCode; }
        public void setTargetStoreCode(String targetStoreCode) { this.targetStoreCode = targetStoreCode; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public String getTargetWarehouseName() { return targetWarehouseName; }
        public void setTargetWarehouseName(String targetWarehouseName) { this.targetWarehouseName = targetWarehouseName; }
        public LocalDate getDepartureDate() { return departureDate; }
        public void setDepartureDate(LocalDate departureDate) { this.departureDate = departureDate; }
        public LocalDate getEtaDate() { return etaDate; }
        public void setEtaDate(LocalDate etaDate) { this.etaDate = etaDate; }
        public LocalDate getDomesticReceivedAt() { return domesticReceivedAt; }
        public void setDomesticReceivedAt(LocalDate domesticReceivedAt) { this.domesticReceivedAt = domesticReceivedAt; }
        public LocalDate getOutboundAt() { return outboundAt; }
        public void setOutboundAt(LocalDate outboundAt) { this.outboundAt = outboundAt; }
        public LocalDate getCustomsReleasedAt() { return customsReleasedAt; }
        public void setCustomsReleasedAt(LocalDate customsReleasedAt) { this.customsReleasedAt = customsReleasedAt; }
        public LocalDate getEtWarehouseReceivedAt() { return etWarehouseReceivedAt; }
        public void setEtWarehouseReceivedAt(LocalDate etWarehouseReceivedAt) { this.etWarehouseReceivedAt = etWarehouseReceivedAt; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getContainerNo() { return containerNo; }
        public void setContainerNo(String containerNo) { this.containerNo = containerNo; }
        public List<ImportPreviewLineView> getLines() { return lines; }
        public void setLines(List<ImportPreviewLineView> lines) { this.lines = lines == null ? Collections.emptyList() : lines; }
        public List<ImportPreviewIssueView> getIssues() { return issues; }
        public void setIssues(List<ImportPreviewIssueView> issues) { this.issues = issues == null ? Collections.emptyList() : issues; }
    }

    public static class ImportPreviewView {
        private Long importBatchId;
        private String mode = "local-db";
        private boolean ready = true;
        private String status;
        private String fileName;
        private Integer totalRowCount;
        private Integer validRowCount;
        private Integer errorCount;
        private Integer warningCount;
        private Integer willCreateBatchCount;
        private Integer willUpsertLineCount;
        private List<ImportPreviewBatchView> batches = Collections.emptyList();
        private List<ImportPreviewIssueView> issues = Collections.emptyList();

        @JsonIgnore
        public List<String> getFieldNames() {
            List<String> names = new ArrayList<>();
            for (Method method : getClass().getMethods()) {
                if (method.getParameterCount() == 0 && method.getName().startsWith("get") && !method.getName().equals("getClass")) {
                    String suffix = method.getName().substring(3);
                    names.add(Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1));
                }
            }
            return names;
        }

        public Long getImportBatchId() { return importBatchId; }
        public void setImportBatchId(Long importBatchId) { this.importBatchId = importBatchId; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public boolean isReady() { return ready; }
        public void setReady(boolean ready) { this.ready = ready; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Integer getTotalRowCount() { return totalRowCount; }
        public void setTotalRowCount(Integer totalRowCount) { this.totalRowCount = totalRowCount; }
        public Integer getValidRowCount() { return validRowCount; }
        public void setValidRowCount(Integer validRowCount) { this.validRowCount = validRowCount; }
        public Integer getErrorCount() { return errorCount; }
        public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
        public Integer getWarningCount() { return warningCount; }
        public void setWarningCount(Integer warningCount) { this.warningCount = warningCount; }
        public Integer getWillCreateBatchCount() { return willCreateBatchCount; }
        public void setWillCreateBatchCount(Integer willCreateBatchCount) { this.willCreateBatchCount = willCreateBatchCount; }
        public Integer getWillUpsertLineCount() { return willUpsertLineCount; }
        public void setWillUpsertLineCount(Integer willUpsertLineCount) { this.willUpsertLineCount = willUpsertLineCount; }
        public List<ImportPreviewBatchView> getBatches() { return batches; }
        public void setBatches(List<ImportPreviewBatchView> batches) { this.batches = batches == null ? Collections.emptyList() : batches; }
        public List<ImportPreviewIssueView> getIssues() { return issues; }
        public void setIssues(List<ImportPreviewIssueView> issues) { this.issues = issues == null ? Collections.emptyList() : issues; }
    }

    public static class ImportConfirmView {
        private Long importBatchId;
        private String status;
        private Integer importedBatchCount;
        private Integer importedLineCount;
        private Integer importedNodeCount;

        public Long getImportBatchId() { return importBatchId; }
        public void setImportBatchId(Long importBatchId) { this.importBatchId = importBatchId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getImportedBatchCount() { return importedBatchCount; }
        public void setImportedBatchCount(Integer importedBatchCount) { this.importedBatchCount = importedBatchCount; }
        public Integer getImportedLineCount() { return importedLineCount; }
        public void setImportedLineCount(Integer importedLineCount) { this.importedLineCount = importedLineCount; }
        public Integer getImportedNodeCount() { return importedNodeCount; }
        public void setImportedNodeCount(Integer importedNodeCount) { this.importedNodeCount = importedNodeCount; }
    }

    public static String toMissingFieldsJson(List<String> missingFields) {
        if (missingFields == null || missingFields.isEmpty()) {
            return "[]";
        }
        return missingFields.stream()
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    public static List<String> parseMissingFields(String value) {
        if (value == null || value.trim().length() <= 2) {
            return Collections.emptyList();
        }
        String trimmed = value.trim();
        String body = trimmed.startsWith("[") && trimmed.endsWith("]")
                ? trimmed.substring(1, trimmed.length() - 1)
                : trimmed;
        if (body.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(body.split(","))
                .map(item -> item.trim().replace("\"", ""))
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }
}
