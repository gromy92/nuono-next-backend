package com.nuono.next.intransit;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
        private String trackingNo;
        private String containerNo;
        private String batchReferenceNo;
        private String remark;
        private String missingFieldsJson;
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
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getContainerNo() { return containerNo; }
        public void setContainerNo(String containerNo) { this.containerNo = containerNo; }
        public String getBatchReferenceNo() { return batchReferenceNo; }
        public void setBatchReferenceNo(String batchReferenceNo) { this.batchReferenceNo = batchReferenceNo; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getMissingFieldsJson() { return missingFieldsJson; }
        public void setMissingFieldsJson(String missingFieldsJson) { this.missingFieldsJson = missingFieldsJson; }
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
        private String trackingNo;
        private String containerNo;
        private String batchReferenceNo;
        private String remark;
        private List<String> missingFields = Collections.emptyList();
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
            view.setTrackingNo(row.getTrackingNo());
            view.setContainerNo(row.getContainerNo());
            view.setBatchReferenceNo(row.getBatchReferenceNo());
            view.setRemark(row.getRemark());
            view.setMissingFields(parseMissingFields(row.getMissingFieldsJson()));
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
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getContainerNo() { return containerNo; }
        public void setContainerNo(String containerNo) { this.containerNo = containerNo; }
        public String getBatchReferenceNo() { return batchReferenceNo; }
        public void setBatchReferenceNo(String batchReferenceNo) { this.batchReferenceNo = batchReferenceNo; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public List<String> getMissingFields() { return missingFields; }
        public void setMissingFields(List<String> missingFields) { this.missingFields = missingFields; }
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
        private List<BatchView> items = Collections.emptyList();

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public boolean isReady() { return ready; }
        public void setReady(boolean ready) { this.ready = ready; }
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
        private String remark;
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
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
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
        private String remark;
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
            view.setRemark(row.getRemark());
            view.setMatchedProductId(row.getMatchedProductId());
            view.setProductSkuParent(row.getProductSkuParent());
            view.setProductTitle(row.getProductTitle());
            view.setProductImageUrl(row.getProductImageUrl());
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
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
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
        private String trackingNo;
        private String remark;
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
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class BatchAggregateRow {
        private Integer skuCount;
        private Integer shippedQuantityTotal;
        private Integer receivedQuantityTotal;
        private Integer remainingQuantityTotal;
        private Integer cartonCountTotal;
        private BigDecimal totalWeightKg;
        private BigDecimal totalVolumeCbm;

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
        private String remark;
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
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
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
        private String trackingNo;
        private String containerNo;
        private String remark;
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
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getContainerNo() { return containerNo; }
        public void setContainerNo(String containerNo) { this.containerNo = containerNo; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
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

        public Long getImportBatchId() { return importBatchId; }
        public void setImportBatchId(Long importBatchId) { this.importBatchId = importBatchId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getImportedBatchCount() { return importedBatchCount; }
        public void setImportedBatchCount(Integer importedBatchCount) { this.importedBatchCount = importedBatchCount; }
        public Integer getImportedLineCount() { return importedLineCount; }
        public void setImportedLineCount(Integer importedLineCount) { this.importedLineCount = importedLineCount; }
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
