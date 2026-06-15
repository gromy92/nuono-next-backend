package com.nuono.next.intransit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class InTransitFreightCostRecords {

    private InTransitFreightCostRecords() {
    }

    public static class ActualFreightBillRow {
        private Long id;
        private Long ownerUserId;
        private Long batchId;
        private Long standardForwarderId;
        private String forwarderCode;
        private String forwarderName;
        private String transportMode;
        private String destinationCode;
        private String targetSiteCode;
        private String sourceType;
        private String sourceSystem;
        private String billNo;
        private String billStatus;
        private LocalDateTime businessOccurredAt;
        private LocalDateTime billDate;
        private LocalDateTime paidAt;
        private String currencyCode;
        private BigDecimal exchangeRateToCny;
        private BigDecimal originalTotalAmount;
        private BigDecimal cnyTotalAmount;
        private BigDecimal freightAmountCny;
        private BigDecimal customsAmountCny;
        private BigDecimal storageAmountCny;
        private BigDecimal handlingAmountCny;
        private BigDecimal deliveryAmountCny;
        private BigDecimal interestAmountCny;
        private BigDecimal postedAmountCny;
        private BigDecimal balanceAmountCny;
        private String rawPayloadJson;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getStandardForwarderId() { return standardForwarderId; }
        public void setStandardForwarderId(Long standardForwarderId) { this.standardForwarderId = standardForwarderId; }
        public String getForwarderCode() { return forwarderCode; }
        public void setForwarderCode(String forwarderCode) { this.forwarderCode = forwarderCode; }
        public String getForwarderName() { return forwarderName; }
        public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getDestinationCode() { return destinationCode; }
        public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getSourceSystem() { return sourceSystem; }
        public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
        public String getBillNo() { return billNo; }
        public void setBillNo(String billNo) { this.billNo = billNo; }
        public String getBillStatus() { return billStatus; }
        public void setBillStatus(String billStatus) { this.billStatus = billStatus; }
        public LocalDateTime getBusinessOccurredAt() { return businessOccurredAt; }
        public void setBusinessOccurredAt(LocalDateTime businessOccurredAt) { this.businessOccurredAt = businessOccurredAt; }
        public LocalDateTime getBillDate() { return billDate; }
        public void setBillDate(LocalDateTime billDate) { this.billDate = billDate; }
        public LocalDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public BigDecimal getExchangeRateToCny() { return exchangeRateToCny; }
        public void setExchangeRateToCny(BigDecimal exchangeRateToCny) { this.exchangeRateToCny = exchangeRateToCny; }
        public BigDecimal getOriginalTotalAmount() { return originalTotalAmount; }
        public void setOriginalTotalAmount(BigDecimal originalTotalAmount) { this.originalTotalAmount = originalTotalAmount; }
        public BigDecimal getCnyTotalAmount() { return cnyTotalAmount; }
        public void setCnyTotalAmount(BigDecimal cnyTotalAmount) { this.cnyTotalAmount = cnyTotalAmount; }
        public BigDecimal getFreightAmountCny() { return freightAmountCny; }
        public void setFreightAmountCny(BigDecimal freightAmountCny) { this.freightAmountCny = freightAmountCny; }
        public BigDecimal getCustomsAmountCny() { return customsAmountCny; }
        public void setCustomsAmountCny(BigDecimal customsAmountCny) { this.customsAmountCny = customsAmountCny; }
        public BigDecimal getStorageAmountCny() { return storageAmountCny; }
        public void setStorageAmountCny(BigDecimal storageAmountCny) { this.storageAmountCny = storageAmountCny; }
        public BigDecimal getHandlingAmountCny() { return handlingAmountCny; }
        public void setHandlingAmountCny(BigDecimal handlingAmountCny) { this.handlingAmountCny = handlingAmountCny; }
        public BigDecimal getDeliveryAmountCny() { return deliveryAmountCny; }
        public void setDeliveryAmountCny(BigDecimal deliveryAmountCny) { this.deliveryAmountCny = deliveryAmountCny; }
        public BigDecimal getInterestAmountCny() { return interestAmountCny; }
        public void setInterestAmountCny(BigDecimal interestAmountCny) { this.interestAmountCny = interestAmountCny; }
        public BigDecimal getPostedAmountCny() { return postedAmountCny; }
        public void setPostedAmountCny(BigDecimal postedAmountCny) { this.postedAmountCny = postedAmountCny; }
        public BigDecimal getBalanceAmountCny() { return balanceAmountCny; }
        public void setBalanceAmountCny(BigDecimal balanceAmountCny) { this.balanceAmountCny = balanceAmountCny; }
        public String getRawPayloadJson() { return rawPayloadJson; }
        public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class ActualFreightComponentRow {
        private Long id;
        private Long ownerUserId;
        private Long actualBillId;
        private Long batchId;
        private Long packageId;
        private String boxNo;
        private String externalBoxNo;
        private String psku;
        private String transportMode;
        private String destinationCode;
        private String targetSiteCode;
        private String rawFeeName;
        private String standardFeeType;
        private BigDecimal chargeQuantity;
        private String chargeUnit;
        private BigDecimal unitPrice;
        private String currencyCode;
        private BigDecimal exchangeRateToCny;
        private BigDecimal originalAmount;
        private BigDecimal cnyAmount;
        private BigDecimal quantity;
        private BigDecimal measuredWeightKg;
        private BigDecimal measuredVolumeCbm;
        private BigDecimal volumeWeightKg;
        private BigDecimal chargeableWeightKg;
        private String allocationBasis;
        private String rawPayloadJson;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getActualBillId() { return actualBillId; }
        public void setActualBillId(Long actualBillId) { this.actualBillId = actualBillId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getPackageId() { return packageId; }
        public void setPackageId(Long packageId) { this.packageId = packageId; }
        public String getBoxNo() { return boxNo; }
        public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
        public String getExternalBoxNo() { return externalBoxNo; }
        public void setExternalBoxNo(String externalBoxNo) { this.externalBoxNo = externalBoxNo; }
        public String getPsku() { return psku; }
        public void setPsku(String psku) { this.psku = psku; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getDestinationCode() { return destinationCode; }
        public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public String getRawFeeName() { return rawFeeName; }
        public void setRawFeeName(String rawFeeName) { this.rawFeeName = rawFeeName; }
        public String getStandardFeeType() { return standardFeeType; }
        public void setStandardFeeType(String standardFeeType) { this.standardFeeType = standardFeeType; }
        public BigDecimal getChargeQuantity() { return chargeQuantity; }
        public void setChargeQuantity(BigDecimal chargeQuantity) { this.chargeQuantity = chargeQuantity; }
        public String getChargeUnit() { return chargeUnit; }
        public void setChargeUnit(String chargeUnit) { this.chargeUnit = chargeUnit; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public BigDecimal getExchangeRateToCny() { return exchangeRateToCny; }
        public void setExchangeRateToCny(BigDecimal exchangeRateToCny) { this.exchangeRateToCny = exchangeRateToCny; }
        public BigDecimal getOriginalAmount() { return originalAmount; }
        public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
        public BigDecimal getCnyAmount() { return cnyAmount; }
        public void setCnyAmount(BigDecimal cnyAmount) { this.cnyAmount = cnyAmount; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getMeasuredWeightKg() { return measuredWeightKg; }
        public void setMeasuredWeightKg(BigDecimal measuredWeightKg) { this.measuredWeightKg = measuredWeightKg; }
        public BigDecimal getMeasuredVolumeCbm() { return measuredVolumeCbm; }
        public void setMeasuredVolumeCbm(BigDecimal measuredVolumeCbm) { this.measuredVolumeCbm = measuredVolumeCbm; }
        public BigDecimal getVolumeWeightKg() { return volumeWeightKg; }
        public void setVolumeWeightKg(BigDecimal volumeWeightKg) { this.volumeWeightKg = volumeWeightKg; }
        public BigDecimal getChargeableWeightKg() { return chargeableWeightKg; }
        public void setChargeableWeightKg(BigDecimal chargeableWeightKg) { this.chargeableWeightKg = chargeableWeightKg; }
        public String getAllocationBasis() { return allocationBasis; }
        public void setAllocationBasis(String allocationBasis) { this.allocationBasis = allocationBasis; }
        public String getRawPayloadJson() { return rawPayloadJson; }
        public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class ActualFreightSyncView {
        private int billCount;
        private int componentCount;
        private BigDecimal totalAmountCny = BigDecimal.ZERO;

        public int getBillCount() { return billCount; }
        public void setBillCount(int billCount) { this.billCount = billCount; }
        public int getComponentCount() { return componentCount; }
        public void setComponentCount(int componentCount) { this.componentCount = componentCount; }
        public BigDecimal getTotalAmountCny() { return totalAmountCny; }
        public void setTotalAmountCny(BigDecimal totalAmountCny) { this.totalAmountCny = totalAmountCny == null ? BigDecimal.ZERO : totalAmountCny; }
    }

    public static class BatchFreightCostView {
        private List<ActualFreightBillRow> bills = Collections.emptyList();
        private List<ActualFreightComponentRow> components = Collections.emptyList();

        public List<ActualFreightBillRow> getBills() { return bills; }
        public void setBills(List<ActualFreightBillRow> bills) {
            this.bills = bills == null ? Collections.emptyList() : bills;
        }
        public List<ActualFreightComponentRow> getComponents() { return components; }
        public void setComponents(List<ActualFreightComponentRow> components) {
            this.components = components == null ? Collections.emptyList() : components;
        }
    }

    public static class FreightStatisticsRow {
        private String month;
        private Long standardForwarderId;
        private String transportMode;
        private String destinationCode;
        private String targetSiteCode;
        private String forwarderCode;
        private String forwarderName;
        private Integer batchCount;
        private Integer billCount;
        private Integer componentCount;
        private BigDecimal totalAmountCny;
        private BigDecimal freightAmountCny;
        private BigDecimal customsAmountCny;
        private BigDecimal chargeableWeightKg;

        public String getMonth() { return month; }
        public void setMonth(String month) { this.month = month; }
        public Long getStandardForwarderId() { return standardForwarderId; }
        public void setStandardForwarderId(Long standardForwarderId) { this.standardForwarderId = standardForwarderId; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getDestinationCode() { return destinationCode; }
        public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public String getForwarderCode() { return forwarderCode; }
        public void setForwarderCode(String forwarderCode) { this.forwarderCode = forwarderCode; }
        public String getForwarderName() { return forwarderName; }
        public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
        public Integer getBatchCount() { return batchCount; }
        public void setBatchCount(Integer batchCount) { this.batchCount = batchCount; }
        public Integer getBillCount() { return billCount; }
        public void setBillCount(Integer billCount) { this.billCount = billCount; }
        public Integer getComponentCount() { return componentCount; }
        public void setComponentCount(Integer componentCount) { this.componentCount = componentCount; }
        public BigDecimal getTotalAmountCny() { return totalAmountCny; }
        public void setTotalAmountCny(BigDecimal totalAmountCny) { this.totalAmountCny = totalAmountCny; }
        public BigDecimal getFreightAmountCny() { return freightAmountCny; }
        public void setFreightAmountCny(BigDecimal freightAmountCny) { this.freightAmountCny = freightAmountCny; }
        public BigDecimal getCustomsAmountCny() { return customsAmountCny; }
        public void setCustomsAmountCny(BigDecimal customsAmountCny) { this.customsAmountCny = customsAmountCny; }
        public BigDecimal getChargeableWeightKg() { return chargeableWeightKg; }
        public void setChargeableWeightKg(BigDecimal chargeableWeightKg) { this.chargeableWeightKg = chargeableWeightKg; }
    }

    public static class FreightStatisticsView {
        private List<FreightStatisticsRow> items = Collections.emptyList();

        public List<FreightStatisticsRow> getItems() { return items; }
        public void setItems(List<FreightStatisticsRow> items) {
            this.items = items == null ? Collections.emptyList() : items;
        }
    }

    public static class SkuFreightCostHistoryRow {
        private String psku;
        private String targetSiteCode;
        private String transportMode;
        private String destinationCode;
        private Long standardForwarderId;
        private String forwarderName;
        private String standardFeeType;
        private BigDecimal quantity;
        private BigDecimal chargeQuantity;
        private String chargeUnit;
        private BigDecimal totalAmountCny;
        private BigDecimal unitAmountCny;
        private LocalDateTime businessOccurredAt;

        public String getPsku() { return psku; }
        public void setPsku(String psku) { this.psku = psku; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getDestinationCode() { return destinationCode; }
        public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }
        public Long getStandardForwarderId() { return standardForwarderId; }
        public void setStandardForwarderId(Long standardForwarderId) { this.standardForwarderId = standardForwarderId; }
        public String getForwarderName() { return forwarderName; }
        public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
        public String getStandardFeeType() { return standardFeeType; }
        public void setStandardFeeType(String standardFeeType) { this.standardFeeType = standardFeeType; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getChargeQuantity() { return chargeQuantity; }
        public void setChargeQuantity(BigDecimal chargeQuantity) { this.chargeQuantity = chargeQuantity; }
        public String getChargeUnit() { return chargeUnit; }
        public void setChargeUnit(String chargeUnit) { this.chargeUnit = chargeUnit; }
        public BigDecimal getTotalAmountCny() { return totalAmountCny; }
        public void setTotalAmountCny(BigDecimal totalAmountCny) { this.totalAmountCny = totalAmountCny; }
        public BigDecimal getUnitAmountCny() { return unitAmountCny; }
        public void setUnitAmountCny(BigDecimal unitAmountCny) { this.unitAmountCny = unitAmountCny; }
        public LocalDateTime getBusinessOccurredAt() { return businessOccurredAt; }
        public void setBusinessOccurredAt(LocalDateTime businessOccurredAt) { this.businessOccurredAt = businessOccurredAt; }
    }

    public static class SkuFreightCostHistoryView {
        private List<SkuFreightCostHistoryRow> items = Collections.emptyList();

        public List<SkuFreightCostHistoryRow> getItems() { return items; }
        public void setItems(List<SkuFreightCostHistoryRow> items) {
            this.items = items == null ? Collections.emptyList() : items;
        }
    }

    public static class ForwarderFreightComparisonRow {
        private Long standardForwarderId;
        private String forwarderCode;
        private String forwarderName;
        private String transportMode;
        private String destinationCode;
        private String targetSiteCode;
        private String standardFeeType;
        private String chargeUnit;
        private BigDecimal totalAmountCny;
        private BigDecimal totalChargeQuantity;
        private BigDecimal totalQuantity;
        private BigDecimal amountPerUnit;
        private Integer shipmentCount;

        public Long getStandardForwarderId() { return standardForwarderId; }
        public void setStandardForwarderId(Long standardForwarderId) { this.standardForwarderId = standardForwarderId; }
        public String getForwarderCode() { return forwarderCode; }
        public void setForwarderCode(String forwarderCode) { this.forwarderCode = forwarderCode; }
        public String getForwarderName() { return forwarderName; }
        public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getDestinationCode() { return destinationCode; }
        public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public String getStandardFeeType() { return standardFeeType; }
        public void setStandardFeeType(String standardFeeType) { this.standardFeeType = standardFeeType; }
        public String getChargeUnit() { return chargeUnit; }
        public void setChargeUnit(String chargeUnit) { this.chargeUnit = chargeUnit; }
        public BigDecimal getTotalAmountCny() { return totalAmountCny; }
        public void setTotalAmountCny(BigDecimal totalAmountCny) { this.totalAmountCny = totalAmountCny; }
        public BigDecimal getTotalChargeQuantity() { return totalChargeQuantity; }
        public void setTotalChargeQuantity(BigDecimal totalChargeQuantity) { this.totalChargeQuantity = totalChargeQuantity; }
        public BigDecimal getTotalQuantity() { return totalQuantity; }
        public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }
        public BigDecimal getAmountPerUnit() { return amountPerUnit; }
        public void setAmountPerUnit(BigDecimal amountPerUnit) { this.amountPerUnit = amountPerUnit; }
        public Integer getShipmentCount() { return shipmentCount; }
        public void setShipmentCount(Integer shipmentCount) { this.shipmentCount = shipmentCount; }
    }

    public static class ForwarderFreightComparisonView {
        private List<ForwarderFreightComparisonRow> items = Collections.emptyList();

        public List<ForwarderFreightComparisonRow> getItems() { return items; }
        public void setItems(List<ForwarderFreightComparisonRow> items) {
            this.items = items == null ? Collections.emptyList() : items;
        }
    }

    public static class RateCardVersionRow {
        private Long id;
        private Long ownerUserId;
        private Long standardForwarderId;
        private String forwarderCode;
        private String forwarderName;
        private String versionNo;
        private String versionName;
        private String transportMode;
        private String destinationCode;
        private String targetSiteCode;
        private String currencyCode;
        private BigDecimal exchangeRateToCny;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveTo;
        private String versionStatus;
        private String sourceType;
        private String rawPayloadJson;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getStandardForwarderId() { return standardForwarderId; }
        public void setStandardForwarderId(Long standardForwarderId) { this.standardForwarderId = standardForwarderId; }
        public String getForwarderCode() { return forwarderCode; }
        public void setForwarderCode(String forwarderCode) { this.forwarderCode = forwarderCode; }
        public String getForwarderName() { return forwarderName; }
        public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
        public String getVersionNo() { return versionNo; }
        public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
        public String getVersionName() { return versionName; }
        public void setVersionName(String versionName) { this.versionName = versionName; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getDestinationCode() { return destinationCode; }
        public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public BigDecimal getExchangeRateToCny() { return exchangeRateToCny; }
        public void setExchangeRateToCny(BigDecimal exchangeRateToCny) { this.exchangeRateToCny = exchangeRateToCny; }
        public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public LocalDateTime getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(LocalDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
        public String getVersionStatus() { return versionStatus; }
        public void setVersionStatus(String versionStatus) { this.versionStatus = versionStatus; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public String getRawPayloadJson() { return rawPayloadJson; }
        public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class RateCardRuleRow {
        private Long id;
        private Long ownerUserId;
        private Long rateCardVersionId;
        private String standardFeeType;
        private String rawFeeName;
        private String productCategory;
        private String boxCategory;
        private String chargeUnit;
        private BigDecimal unitPrice;
        private BigDecimal minChargeQuantity;
        private BigDecimal minAmountCny;
        private String currencyCode;
        private BigDecimal exchangeRateToCny;
        private String ruleStatus;
        private String formulaJson;
        private String rawPayloadJson;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getRateCardVersionId() { return rateCardVersionId; }
        public void setRateCardVersionId(Long rateCardVersionId) { this.rateCardVersionId = rateCardVersionId; }
        public String getStandardFeeType() { return standardFeeType; }
        public void setStandardFeeType(String standardFeeType) { this.standardFeeType = standardFeeType; }
        public String getRawFeeName() { return rawFeeName; }
        public void setRawFeeName(String rawFeeName) { this.rawFeeName = rawFeeName; }
        public String getProductCategory() { return productCategory; }
        public void setProductCategory(String productCategory) { this.productCategory = productCategory; }
        public String getBoxCategory() { return boxCategory; }
        public void setBoxCategory(String boxCategory) { this.boxCategory = boxCategory; }
        public String getChargeUnit() { return chargeUnit; }
        public void setChargeUnit(String chargeUnit) { this.chargeUnit = chargeUnit; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getMinChargeQuantity() { return minChargeQuantity; }
        public void setMinChargeQuantity(BigDecimal minChargeQuantity) { this.minChargeQuantity = minChargeQuantity; }
        public BigDecimal getMinAmountCny() { return minAmountCny; }
        public void setMinAmountCny(BigDecimal minAmountCny) { this.minAmountCny = minAmountCny; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public BigDecimal getExchangeRateToCny() { return exchangeRateToCny; }
        public void setExchangeRateToCny(BigDecimal exchangeRateToCny) { this.exchangeRateToCny = exchangeRateToCny; }
        public String getRuleStatus() { return ruleStatus; }
        public void setRuleStatus(String ruleStatus) { this.ruleStatus = ruleStatus; }
        public String getFormulaJson() { return formulaJson; }
        public void setFormulaJson(String formulaJson) { this.formulaJson = formulaJson; }
        public String getRawPayloadJson() { return rawPayloadJson; }
        public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class RateCardVersionView {
        private Long rateCardVersionId;
        private int ruleCount;

        public Long getRateCardVersionId() { return rateCardVersionId; }
        public void setRateCardVersionId(Long rateCardVersionId) { this.rateCardVersionId = rateCardVersionId; }
        public int getRuleCount() { return ruleCount; }
        public void setRuleCount(int ruleCount) { this.ruleCount = ruleCount; }
    }

    public static class EstimateSnapshotRow {
        private Long id;
        private Long ownerUserId;
        private Long batchId;
        private String sourceEstimateType;
        private Long sourceEstimateId;
        private String sourceEstimateNo;
        private Long sourceRecommendationId;
        private Long rateCardVersionId;
        private Long standardForwarderId;
        private String forwarderCode;
        private String forwarderName;
        private String transportMode;
        private String destinationCode;
        private String targetSiteCode;
        private Boolean recommended;
        private String estimateStatus;
        private String currencyCode;
        private BigDecimal exchangeRateToCny;
        private BigDecimal estimatedTotalAmount;
        private BigDecimal estimatedTotalCny;
        private LocalDateTime generatedAt;
        private String rawPayloadJson;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getSourceEstimateType() { return sourceEstimateType; }
        public void setSourceEstimateType(String sourceEstimateType) { this.sourceEstimateType = sourceEstimateType; }
        public Long getSourceEstimateId() { return sourceEstimateId; }
        public void setSourceEstimateId(Long sourceEstimateId) { this.sourceEstimateId = sourceEstimateId; }
        public String getSourceEstimateNo() { return sourceEstimateNo; }
        public void setSourceEstimateNo(String sourceEstimateNo) { this.sourceEstimateNo = sourceEstimateNo; }
        public Long getSourceRecommendationId() { return sourceRecommendationId; }
        public void setSourceRecommendationId(Long sourceRecommendationId) { this.sourceRecommendationId = sourceRecommendationId; }
        public Long getRateCardVersionId() { return rateCardVersionId; }
        public void setRateCardVersionId(Long rateCardVersionId) { this.rateCardVersionId = rateCardVersionId; }
        public Long getStandardForwarderId() { return standardForwarderId; }
        public void setStandardForwarderId(Long standardForwarderId) { this.standardForwarderId = standardForwarderId; }
        public String getForwarderCode() { return forwarderCode; }
        public void setForwarderCode(String forwarderCode) { this.forwarderCode = forwarderCode; }
        public String getForwarderName() { return forwarderName; }
        public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
        public String getDestinationCode() { return destinationCode; }
        public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public Boolean getRecommended() { return recommended; }
        public void setRecommended(Boolean recommended) { this.recommended = recommended; }
        public String getEstimateStatus() { return estimateStatus; }
        public void setEstimateStatus(String estimateStatus) { this.estimateStatus = estimateStatus; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public BigDecimal getExchangeRateToCny() { return exchangeRateToCny; }
        public void setExchangeRateToCny(BigDecimal exchangeRateToCny) { this.exchangeRateToCny = exchangeRateToCny; }
        public BigDecimal getEstimatedTotalAmount() { return estimatedTotalAmount; }
        public void setEstimatedTotalAmount(BigDecimal estimatedTotalAmount) { this.estimatedTotalAmount = estimatedTotalAmount; }
        public BigDecimal getEstimatedTotalCny() { return estimatedTotalCny; }
        public void setEstimatedTotalCny(BigDecimal estimatedTotalCny) { this.estimatedTotalCny = estimatedTotalCny; }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
        public String getRawPayloadJson() { return rawPayloadJson; }
        public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class EstimateComponentRow {
        private Long id;
        private Long ownerUserId;
        private Long estimateSnapshotId;
        private String targetSiteCode;
        private String psku;
        private String componentType;
        private String rawFeeName;
        private BigDecimal quantity;
        private BigDecimal chargeableWeightKg;
        private BigDecimal chargeQuantity;
        private String chargeUnit;
        private BigDecimal unitPrice;
        private String currencyCode;
        private BigDecimal exchangeRateToCny;
        private BigDecimal estimatedAmount;
        private BigDecimal estimatedAmountCny;
        private String rawPayloadJson;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getEstimateSnapshotId() { return estimateSnapshotId; }
        public void setEstimateSnapshotId(Long estimateSnapshotId) { this.estimateSnapshotId = estimateSnapshotId; }
        public String getTargetSiteCode() { return targetSiteCode; }
        public void setTargetSiteCode(String targetSiteCode) { this.targetSiteCode = targetSiteCode; }
        public String getPsku() { return psku; }
        public void setPsku(String psku) { this.psku = psku; }
        public String getComponentType() { return componentType; }
        public void setComponentType(String componentType) { this.componentType = componentType; }
        public String getRawFeeName() { return rawFeeName; }
        public void setRawFeeName(String rawFeeName) { this.rawFeeName = rawFeeName; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getChargeableWeightKg() { return chargeableWeightKg; }
        public void setChargeableWeightKg(BigDecimal chargeableWeightKg) { this.chargeableWeightKg = chargeableWeightKg; }
        public BigDecimal getChargeQuantity() { return chargeQuantity; }
        public void setChargeQuantity(BigDecimal chargeQuantity) { this.chargeQuantity = chargeQuantity; }
        public String getChargeUnit() { return chargeUnit; }
        public void setChargeUnit(String chargeUnit) { this.chargeUnit = chargeUnit; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public BigDecimal getExchangeRateToCny() { return exchangeRateToCny; }
        public void setExchangeRateToCny(BigDecimal exchangeRateToCny) { this.exchangeRateToCny = exchangeRateToCny; }
        public BigDecimal getEstimatedAmount() { return estimatedAmount; }
        public void setEstimatedAmount(BigDecimal estimatedAmount) { this.estimatedAmount = estimatedAmount; }
        public BigDecimal getEstimatedAmountCny() { return estimatedAmountCny; }
        public void setEstimatedAmountCny(BigDecimal estimatedAmountCny) { this.estimatedAmountCny = estimatedAmountCny; }
        public String getRawPayloadJson() { return rawPayloadJson; }
        public void setRawPayloadJson(String rawPayloadJson) { this.rawPayloadJson = rawPayloadJson; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class EstimateMatchRow {
        private Long id;
        private Long ownerUserId;
        private Long batchId;
        private Long actualBillId;
        private Long estimateSnapshotId;
        private String matchStatus;
        private BigDecimal actualTotalCny;
        private BigDecimal estimatedTotalCny;
        private BigDecimal diffAmountCny;
        private BigDecimal diffRate;
        private LocalDateTime matchedAt;
        private String reason;
        private Long createdBy;
        private Long updatedBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public Long getActualBillId() { return actualBillId; }
        public void setActualBillId(Long actualBillId) { this.actualBillId = actualBillId; }
        public Long getEstimateSnapshotId() { return estimateSnapshotId; }
        public void setEstimateSnapshotId(Long estimateSnapshotId) { this.estimateSnapshotId = estimateSnapshotId; }
        public String getMatchStatus() { return matchStatus; }
        public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
        public BigDecimal getActualTotalCny() { return actualTotalCny; }
        public void setActualTotalCny(BigDecimal actualTotalCny) { this.actualTotalCny = actualTotalCny; }
        public BigDecimal getEstimatedTotalCny() { return estimatedTotalCny; }
        public void setEstimatedTotalCny(BigDecimal estimatedTotalCny) { this.estimatedTotalCny = estimatedTotalCny; }
        public BigDecimal getDiffAmountCny() { return diffAmountCny; }
        public void setDiffAmountCny(BigDecimal diffAmountCny) { this.diffAmountCny = diffAmountCny; }
        public BigDecimal getDiffRate() { return diffRate; }
        public void setDiffRate(BigDecimal diffRate) { this.diffRate = diffRate; }
        public LocalDateTime getMatchedAt() { return matchedAt; }
        public void setMatchedAt(LocalDateTime matchedAt) { this.matchedAt = matchedAt; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Long getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    }

    public static class EstimateSnapshotView {
        private Long estimateSnapshotId;
        private int componentCount;

        public Long getEstimateSnapshotId() { return estimateSnapshotId; }
        public void setEstimateSnapshotId(Long estimateSnapshotId) { this.estimateSnapshotId = estimateSnapshotId; }
        public int getComponentCount() { return componentCount; }
        public void setComponentCount(int componentCount) { this.componentCount = componentCount; }
    }
}
