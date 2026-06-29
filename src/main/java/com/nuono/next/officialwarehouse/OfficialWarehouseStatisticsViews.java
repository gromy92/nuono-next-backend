package com.nuono.next.officialwarehouse;

import java.util.ArrayList;
import java.util.List;

public final class OfficialWarehouseStatisticsViews {

    private OfficialWarehouseStatisticsViews() {
    }

    public static class StockStatisticsQuery {
        public String storeCode;
        public String siteCode;
        public String keyword;
        public String warehouseCode;
        public String stockBucket;
    }

    public static class StockStatisticsView {
        public StockStatisticsSummaryView summary = new StockStatisticsSummaryView();
        public List<StockStatisticsRowView> rows = new ArrayList<>();
    }

    public static class StockStatisticsSummaryView {
        public long effectiveStock;
        public long currentStock;
        public long returnStock;
        public long failedOrExceptionStock;
        public long pendingConfirmationStock;
        public int skuCount;
        public int exceptionSkuCount;
    }

    public static class StockStatisticsRowView {
        public String productMasterId;
        public String productVariantId;
        public String productSiteOfferId;
        public String logicalStoreId;
        public String storeCode;
        public String storeName;
        public String siteCode;
        public String projectCode;
        public String partnerId;
        public String skuParent;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String title;
        public String titleCn;
        public String titleEn;
        public String brand;
        public String imageUrl;
        public String warehouseCode;
        public long currentStock;
        public long effectiveStock;
        public long returnStock;
        public long failedOrExceptionStock;
        public long pendingConfirmationStock;
        public String sourceType;
        public String inventoryConfidence;
        public String lastSyncedAt;
        public List<String> anomalyFlags = new ArrayList<>();
        public List<StockWarehouseStockView> warehouseStocks = new ArrayList<>();
    }

    public static class StockWarehouseStockView {
        public String warehouseCode;
        public long currentStock;
        public long effectiveStock;
        public long returnStock;
        public long failedOrExceptionStock;
        public long pendingConfirmationStock;
    }

    public static class InboundStatisticsView {
        public InboundStatisticsSummaryView summary = new InboundStatisticsSummaryView();
        public List<InboundStageRowView> rows = new ArrayList<>();
    }

    public static class InboundStatisticsSummaryView {
        public int asnCount;
        public long totalQuantity;
        public int appointmentScheduledCount;
        public int appointmentPendingCount;
        public int appointmentFailedCount;
        public int receivingAsnCount;
        public int grnCompletedAsnCount;
        public int failedAsnCount;
        public boolean lineReceiptReportConnected;
        public String latestReceiptImportId;
        public String latestReceiptImportedAt;
        public int receiptLineCount;
        public long expectedQuantity;
        public long receivedQuantity;
        public long qcFailedQuantity;
        public long unidentifiedQuantity;
        public int normalLineCount;
        public int qcFailedLineCount;
        public int shortReceivedLineCount;
        public int overReceivedLineCount;
        public int unidentifiedLineCount;
        public int matchedLineCount;
        public int noLocalAsnLineCount;
        public int lineUnmatchedLineCount;
        public int productUnmatchedLineCount;
        public int receiptExceptionLineCount;
        public boolean scheduledDeliveryAccuracyConnected;
        public String latestScheduledDeliveryAccuracyImportId;
        public String latestScheduledDeliveryAccuracyImportedAt;
        public int scheduledDeliveryAccuracyAsnCount;
        public long scheduledQuantity;
        public long grnQuantity;
        public long inboundQuantityVariance;
        public int putawayCompletedAsnCount;
        public int cancelledAsnCount;
        public int expiredAsnCount;
        public int matchedScheduledDeliveryAccuracyAsnCount;
        public int noLocalScheduledDeliveryAccuracyAsnCount;
        public int scheduledDeliveryAccuracyExceptionAsnCount;
    }

    public static class InboundStageRowView {
        public String asnId;
        public String localAsnNo;
        public String noonAsnNr;
        public String storeCode;
        public String siteCode;
        public String localStatus;
        public String noonAsnStatus;
        public String inboundStage;
        public String appointmentStatus;
        public int totalQuantity;
        public String selectedWarehouseCode;
        public String selectedWarehousePartnerCode;
    }

    public static class ProductInboundHistoryView {
        public ProductInboundHistorySummaryView summary = new ProductInboundHistorySummaryView();
        public List<ProductInboundReceiptRowView> rows = new ArrayList<>();
        public List<ProductStockSourceCandidateView> sourceCandidates = new ArrayList<>();
    }

    public static class ProductInboundHistorySummaryView {
        public int receiptLineCount;
        public long expectedQuantity;
        public long receivedQuantity;
        public long qcFailedQuantity;
        public long unidentifiedQuantity;
        public int exceptionLineCount;
    }

    public static class ProductInboundReceiptRowView {
        public String importId;
        public String reportRowId;
        public String noonAsnNr;
        public String partnerSku;
        public String pskuCode;
        public String noonSku;
        public String pbarcodeCanonical;
        public String partnerWarehouse;
        public String noonWarehouse;
        public int qtyExpected;
        public int receivedQty;
        public int qcFailedQty;
        public int unidentifiedQty;
        public String qcFailedReason;
        public String receiptStatus;
        public String matchStatus;
        public String asnCreatedAt;
        public String asnScheduleDate;
        public String asnCompletedAt;
        public String importedAt;
    }

    public static class ProductStockSourceCandidateView {
        public String logisticsBatchId;
        public String logisticsBatchNo;
        public String logisticsStatus;
        public String purchaseOrderId;
        public String purchaseOrderNo;
        public String sourceStoreCode;
        public String siteCode;
        public String partnerSku;
        public String skuParent;
        public long quantity;
        public String latestAt;
        public String relationBasis;
    }

    public static class InventorySyncResultView {
        public String syncBatchId;
        public String storeCode;
        public String siteCode;
        public int pageCount;
        public int fetchedRows;
        public int insertedRows;
        public String sourceType;
        public String syncedAt;
    }

    public static class FbnExportListView {
        public String storeCode;
        public String siteCode;
        public int page;
        public int perPage;
        public boolean hasNextPage;
        public String sourceType;
        public List<FbnExportItemView> items = new ArrayList<>();
    }

    public static class FbnExportItemView {
        public String exportCode;
        public String status;
        public String reportType;
        public String fileName;
        public String createdAt;
        public String downloadUrl;
    }

    public static class FbnExportStatusView {
        public String storeCode;
        public String siteCode;
        public String exportCode;
        public String status;
        public String fileName;
        public String downloadUrl;
        public String message;
        public Integer totalRows;
        public String sourceType;
    }

    public static class FbnExportCreateView {
        public String storeCode;
        public String siteCode;
        public String exportCode;
        public String status;
        public String reportType;
        public String fromDate;
        public String toDate;
        public String sourceType;
    }

    public static class FbnReceivedImportResultView {
        public String importId;
        public String storeCode;
        public String siteCode;
        public String exportCode;
        public String reportType;
        public String status;
        public Integer totalRows;
        public Integer validRows;
        public Integer warningRows;
        public Integer errorRows;
        public Integer insertedReceiptLines;
        public String fileName;
        public String fileSha256;
        public String importedAt;
        public String sourceType;
    }

    public static class ScheduledDeliveryAccuracyImportResultView {
        public String importId;
        public String storeCode;
        public String siteCode;
        public String exportCode;
        public String reportType;
        public String status;
        public Integer totalRows;
        public Integer validRows;
        public Integer warningRows;
        public Integer errorRows;
        public Integer insertedAsnRows;
        public Integer scheduledQuantity;
        public Integer grnQuantity;
        public Integer inboundQuantityVariance;
        public String fileName;
        public String fileSha256;
        public String importedAt;
        public String sourceType;
    }

    public static class ScheduledDeliveryAccuracyRematchResultView {
        public String importId;
        public String storeCode;
        public String siteCode;
        public int totalRows;
        public int matchedRowsBefore;
        public int noLocalAsnRowsBefore;
        public int rematchedRows;
        public int matchedRowsAfter;
        public int noLocalAsnRowsAfter;
    }

    public static class ScheduledDeliveryAccuracyMissingAsnSyncResultView {
        public String importId;
        public String storeCode;
        public String siteCode;
        public boolean dryRun;
        public int missingAsnCount;
        public int requestedAsnCount;
        public int foundAsnCount;
        public int notFoundAsnCount;
        public int created;
        public int updated;
        public int scheduled;
        public int corrected;
        public int failed;
        public int skipped;
        public ScheduledDeliveryAccuracyRematchResultView rematch;
    }
}
