package com.nuono.next.officialwarehouse;

public final class OfficialWarehouseStatisticsRecords {

    private OfficialWarehouseStatisticsRecords() {
    }

    public static class StockSourceRecord {
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String skuParent;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String title;
        public String brand;
        public String imageUrl;
        public String warehouseCode;
        public Integer fbnStock;
        public Integer supermallStock;
        public Integer fbpStock;
        public String lastSyncedAt;
    }

    public static class InventorySnapshotSourceRecord {
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String skuParent;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String title;
        public String brand;
        public String imageUrl;
        public String warehouseCode;
        public Long currentStock;
        public Long effectiveStock;
        public Long returnStock;
        public Long failedOrExceptionStock;
        public Long pendingConfirmationStock;
        public String inventoryConfidence;
        public String lastSyncedAt;
    }

    public static class InventoryWarehouseStockRecord {
        public Long productSiteOfferId;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String warehouseCode;
        public Long currentStock;
        public Long effectiveStock;
        public Long returnStock;
        public Long failedOrExceptionStock;
        public Long pendingConfirmationStock;
    }

    public static class InventorySyncScopeRecord {
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String siteCode;
        public String projectCode;
        public String partnerId;
    }

    public static class InventoryLineProductMatchRecord {
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String skuParent;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String title;
        public String brand;
    }

    public static class InventorySyncBatchInsertRecord {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public String sourceType;
        public String requestSummaryJson;
        public String responseSummaryJson;
        public String status;
        public Integer totalPages;
        public Integer totalRows;
        public Integer validRows;
        public Integer errorRows;
        public Long operatorUserId;
    }

    public static class InventorySnapshotLineInsertRecord {
        public Long id;
        public Long syncBatchId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String pbarcode;
        public String barcode;
        public String warehouseCode;
        public String countryCode;
        public String inventoryType;
        public String reasonCode;
        public String classificationCode;
        public String stockBucket;
        public Integer quantity;
        public String inventorySnapshotAt;
        public String titleCache;
        public String brandCache;
        public String matchStatus;
        public String matchMessage;
        public String rawPayloadJson;
        public Long operatorUserId;
    }

    public static class ReportImportInsertRecord {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public String reportType;
        public String sourceType;
        public String sourceExportCode;
        public String fileName;
        public String fileSha256;
        public String snapshotAt;
        public String businessDateStart;
        public String businessDateEnd;
        public Integer totalRows;
        public Integer validRows;
        public Integer warningRows;
        public Integer errorRows;
        public String status;
        public String summaryJson;
        public String rawPreviewJson;
        public Long operatorUserId;
    }

    public static class ReportRowInsertRecord {
        public Long id;
        public Long importId;
        public String reportType;
        public Integer rowNo;
        public String businessKey;
        public String businessKeyHash;
        public String rowStatus;
        public String warningCode;
        public String errorMessage;
        public String rawRowJson;
        public String normalizedRowJson;
        public Long operatorUserId;
    }

    public static class InboundReceiptAsnMatchRecord {
        public Long asnId;
        public Long appointmentId;
        public String localAsnNo;
        public String noonAsnNr;
    }

    public static class InboundReceiptAsnLineMatchRecord {
        public Long asnLineId;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
    }

    public static class InboundReceiptLineInsertRecord {
        public Long id;
        public Long importId;
        public Long reportRowId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public Long asnId;
        public Long asnLineId;
        public String noonAsnNr;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String pbarcodeCanonical;
        public String partnerWarehouse;
        public String noonWarehouse;
        public String countryCode;
        public Integer qtyExpected;
        public Integer receivedQty;
        public Integer qcFailedQty;
        public Integer unidentifiedQty;
        public String qcFailedReason;
        public String receiptStatus;
        public String matchStatus;
        public String anomalyFlagsJson;
        public String asnCreatedAt;
        public String asnScheduleDate;
        public String asnCompletedAt;
        public String rawPayloadJson;
        public Long operatorUserId;
    }

    public static class InboundReceiptHistoryRecord {
        public Long importId;
        public Long reportRowId;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String noonAsnNr;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String pbarcodeCanonical;
        public String partnerWarehouse;
        public String noonWarehouse;
        public Integer qtyExpected;
        public Integer receivedQty;
        public Integer qcFailedQty;
        public Integer unidentifiedQty;
        public String qcFailedReason;
        public String receiptStatus;
        public String matchStatus;
        public String anomalyFlagsJson;
        public String asnCreatedAt;
        public String asnScheduleDate;
        public String asnCompletedAt;
        public String importedAt;
    }

    public static class ProductStockSourceCandidateRecord {
        public Long logisticsBatchId;
        public String logisticsBatchNo;
        public String logisticsStatus;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public String sourceStoreCode;
        public String siteCode;
        public String partnerSku;
        public String skuParent;
        public Long quantity;
        public String latestAt;
    }

    public static class DeliveryAccuracyAsnInsertRecord {
        public Long id;
        public Long importId;
        public Long reportRowId;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public Long asnId;
        public Long appointmentId;
        public String noonAsnNr;
        public String warehouseCode;
        public String countryCode;
        public String asnCreationDate;
        public String scheduledDate;
        public String deliveryDate;
        public Integer scheduledQty;
        public Integer grnQty;
        public Integer inboundQtyVariance;
        public String accuracyStatus;
        public String inboundUtilizationEfficiency;
        public String matchStatus;
        public String anomalyFlagsJson;
        public String rawPayloadJson;
        public Long operatorUserId;
    }

    public static class DeliveryAccuracyRematchSummaryRecord {
        public Integer totalRows;
        public Integer matchedRows;
        public Integer noLocalAsnRows;
    }

    public static class ScheduledDeliveryAccuracySummaryRecord {
        public Long latestImportId;
        public String latestImportedAt;
        public Integer asnCount;
        public Long scheduledQuantity;
        public Long grnQuantity;
        public Long inboundQuantityVariance;
        public Integer putawayCompletedAsnCount;
        public Integer cancelledAsnCount;
        public Integer expiredAsnCount;
        public Integer matchedAsnCount;
        public Integer noLocalAsnCount;
        public Integer exceptionAsnCount;
    }

    public static class InboundReceiptSummaryRecord {
        public Long latestImportId;
        public String latestImportedAt;
        public Integer asnCount;
        public Integer receiptLineCount;
        public Long expectedQuantity;
        public Long receivedQuantity;
        public Long qcFailedQuantity;
        public Long unidentifiedQuantity;
        public Integer normalLineCount;
        public Integer qcFailedLineCount;
        public Integer shortReceivedLineCount;
        public Integer overReceivedLineCount;
        public Integer unidentifiedLineCount;
        public Integer matchedLineCount;
        public Integer noLocalAsnLineCount;
        public Integer lineUnmatchedLineCount;
        public Integer productUnmatchedLineCount;
        public Integer receiptExceptionLineCount;
    }

    public static class StockCorrectionEventRecord {
        public Long id;
        public Long ownerUserId;
        public String storeCode;
        public String siteCode;
        public String correctionType;
        public String targetRefType;
        public Long targetRefId;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String warehouseCode;
        public String fromStockBucket;
        public String toStockBucket;
        public Integer quantity;
        public String reasonCode;
        public String reasonText;
    }

    public static class StockCorrectionInsertRecord {
        public Long id;
        public Long ownerUserId;
        public Long logicalStoreId;
        public String storeCode;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public String correctionType;
        public String targetRefType;
        public Long targetRefId;
        public Long productMasterId;
        public Long productVariantId;
        public Long productSiteOfferId;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String warehouseCode;
        public String fromStockBucket;
        public String toStockBucket;
        public Integer quantity;
        public String reasonCode;
        public String reasonText;
        public String beforePayloadJson;
        public String afterPayloadJson;
        public Long operatorUserId;
    }

    public static class InboundStageRecord {
        public Long asnId;
        public String localAsnNo;
        public String noonAsnNr;
        public String storeCode;
        public String siteCode;
        public String status;
        public String noonAsnStatus;
        public Integer totalQuantity;
        public Integer noonTotalQty;
        public String selectedWarehouseCode;
        public String selectedWarehousePartnerCode;
        public String appointmentStatus;
    }
}
