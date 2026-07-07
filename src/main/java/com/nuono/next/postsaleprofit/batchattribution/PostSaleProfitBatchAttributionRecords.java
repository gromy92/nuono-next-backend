package com.nuono.next.postsaleprofit.batchattribution;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PostSaleProfitBatchAttributionRecords {
    private PostSaleProfitBatchAttributionRecords() {
    }

    public static class BatchAttributionSummaryView {
        private int stockedSkuCount;
        private BigDecimal stockQuantity = BigDecimal.ZERO;
        private BigDecimal sellableStockQuantity = BigDecimal.ZERO;
        private BigDecimal soldQuantity = BigDecimal.ZERO;
        private int closedSkuCount;
        private int fifoReadySkuCount;
        private int missingAsnSkuCount;
        private int missingLogisticsSkuCount;
        private int missingPurchaseSkuCount;
        private int ambiguousSkuCount;
        private int logisticsLinkedPurchaseMissingSkuCount;

        public int getStockedSkuCount() { return stockedSkuCount; }
        public void setStockedSkuCount(int stockedSkuCount) { this.stockedSkuCount = stockedSkuCount; }
        public BigDecimal getStockQuantity() { return stockQuantity; }
        public void setStockQuantity(BigDecimal stockQuantity) { this.stockQuantity = stockQuantity; }
        public BigDecimal getSellableStockQuantity() { return sellableStockQuantity; }
        public void setSellableStockQuantity(BigDecimal sellableStockQuantity) { this.sellableStockQuantity = sellableStockQuantity; }
        public BigDecimal getSoldQuantity() { return soldQuantity; }
        public void setSoldQuantity(BigDecimal soldQuantity) { this.soldQuantity = soldQuantity; }
        public int getClosedSkuCount() { return closedSkuCount; }
        public void setClosedSkuCount(int closedSkuCount) { this.closedSkuCount = closedSkuCount; }
        public int getFifoReadySkuCount() { return fifoReadySkuCount; }
        public void setFifoReadySkuCount(int fifoReadySkuCount) { this.fifoReadySkuCount = fifoReadySkuCount; }
        public int getMissingAsnSkuCount() { return missingAsnSkuCount; }
        public void setMissingAsnSkuCount(int missingAsnSkuCount) { this.missingAsnSkuCount = missingAsnSkuCount; }
        public int getMissingLogisticsSkuCount() { return missingLogisticsSkuCount; }
        public void setMissingLogisticsSkuCount(int missingLogisticsSkuCount) { this.missingLogisticsSkuCount = missingLogisticsSkuCount; }
        public int getMissingPurchaseSkuCount() { return missingPurchaseSkuCount; }
        public void setMissingPurchaseSkuCount(int missingPurchaseSkuCount) { this.missingPurchaseSkuCount = missingPurchaseSkuCount; }
        public int getAmbiguousSkuCount() { return ambiguousSkuCount; }
        public void setAmbiguousSkuCount(int ambiguousSkuCount) { this.ambiguousSkuCount = ambiguousSkuCount; }
        public int getLogisticsLinkedPurchaseMissingSkuCount() { return logisticsLinkedPurchaseMissingSkuCount; }
        public void setLogisticsLinkedPurchaseMissingSkuCount(int logisticsLinkedPurchaseMissingSkuCount) {
            this.logisticsLinkedPurchaseMissingSkuCount = logisticsLinkedPurchaseMissingSkuCount;
        }
    }

    public static class BatchAttributionListView {
        private BatchAttributionSummaryView summary = new BatchAttributionSummaryView();
        private List<BatchAttributionSkuView> rows = List.of();

        public BatchAttributionListView() {
        }

        public BatchAttributionListView(BatchAttributionSummaryView summary, List<BatchAttributionSkuView> rows) {
            this.summary = summary == null ? new BatchAttributionSummaryView() : summary;
            this.rows = rows == null ? List.of() : rows;
        }

        public BatchAttributionSummaryView getSummary() { return summary; }
        public void setSummary(BatchAttributionSummaryView summary) { this.summary = summary; }
        public List<BatchAttributionSkuView> getRows() { return rows; }
        public void setRows(List<BatchAttributionSkuView> rows) { this.rows = rows == null ? List.of() : rows; }
    }

    public static class BatchAttributionSkuRow {
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public BigDecimal stockQuantity;
        public BigDecimal sellableStockQuantity;
        public BigDecimal soldQuantity;
        public Integer purchaseOrderCount;
        public Integer purchaseOrderNoCount;
        public String purchaseOrderNos;
        public BigDecimal purchaseQuantity;
        public Integer inTransitBatchCount;
        public String logisticsBatchNos;
        public BigDecimal inTransitShippedQuantity;
        public Integer asnCount;
        public String asnNos;
        public BigDecimal asnQuantity;
        public Integer bridgeLogisticsCount;
        public Integer bridgePurchaseOrderCount;
        public String bridgeLogisticsNos;
        public String bridgePurchaseOrderNos;
    }

    public static class BatchAttributionSkuView {
        private String partnerSku;
        private String skuParent;
        private String productTitle;
        private String productImageUrl;
        private BigDecimal stockQuantity = BigDecimal.ZERO;
        private BigDecimal sellableStockQuantity = BigDecimal.ZERO;
        private BigDecimal soldQuantity = BigDecimal.ZERO;
        private int purchaseOrderCount;
        private String purchaseOrderNos;
        private BigDecimal purchaseQuantity = BigDecimal.ZERO;
        private int inTransitBatchCount;
        private String logisticsBatchNos;
        private BigDecimal inTransitShippedQuantity = BigDecimal.ZERO;
        private int asnCount;
        private String asnNos;
        private BigDecimal asnQuantity = BigDecimal.ZERO;
        private int bridgeLogisticsCount;
        private int bridgePurchaseOrderCount;
        private String bridgeLogisticsNos;
        private String bridgePurchaseOrderNos;
        private String attributionStatus;
        private String blocker;
        private boolean fifoReady;

        public static BatchAttributionSkuView from(BatchAttributionSkuRow row, String status, String blocker) {
            BatchAttributionSkuView view = new BatchAttributionSkuView();
            if (row == null) {
                return view;
            }
            view.partnerSku = row.partnerSku;
            view.skuParent = row.skuParent;
            view.productTitle = row.productTitle;
            view.productImageUrl = row.productImageUrl;
            view.stockQuantity = value(row.stockQuantity);
            view.sellableStockQuantity = value(row.sellableStockQuantity);
            view.soldQuantity = value(row.soldQuantity);
            view.purchaseOrderCount = count(row.purchaseOrderNoCount != null ? row.purchaseOrderNoCount : row.purchaseOrderCount);
            view.purchaseOrderNos = row.purchaseOrderNos;
            view.purchaseQuantity = value(row.purchaseQuantity);
            view.inTransitBatchCount = count(row.inTransitBatchCount);
            view.logisticsBatchNos = row.logisticsBatchNos;
            view.inTransitShippedQuantity = value(row.inTransitShippedQuantity);
            view.asnCount = count(row.asnCount);
            view.asnNos = row.asnNos;
            view.asnQuantity = value(row.asnQuantity);
            view.bridgeLogisticsCount = count(row.bridgeLogisticsCount);
            view.bridgePurchaseOrderCount = count(row.bridgePurchaseOrderCount);
            view.bridgeLogisticsNos = row.bridgeLogisticsNos;
            view.bridgePurchaseOrderNos = row.bridgePurchaseOrderNos;
            view.attributionStatus = status;
            view.blocker = blocker;
            view.fifoReady = "fifo_ready".equals(status) || "closed".equals(status);
            return view;
        }

        public String getPartnerSku() { return partnerSku; }
        public String getSkuParent() { return skuParent; }
        public String getProductTitle() { return productTitle; }
        public String getProductImageUrl() { return productImageUrl; }
        public BigDecimal getStockQuantity() { return stockQuantity; }
        public BigDecimal getSellableStockQuantity() { return sellableStockQuantity; }
        public BigDecimal getSoldQuantity() { return soldQuantity; }
        public int getPurchaseOrderCount() { return purchaseOrderCount; }
        public String getPurchaseOrderNos() { return purchaseOrderNos; }
        public BigDecimal getPurchaseQuantity() { return purchaseQuantity; }
        public int getInTransitBatchCount() { return inTransitBatchCount; }
        public String getLogisticsBatchNos() { return logisticsBatchNos; }
        public BigDecimal getInTransitShippedQuantity() { return inTransitShippedQuantity; }
        public int getAsnCount() { return asnCount; }
        public String getAsnNos() { return asnNos; }
        public BigDecimal getAsnQuantity() { return asnQuantity; }
        public int getBridgeLogisticsCount() { return bridgeLogisticsCount; }
        public int getBridgePurchaseOrderCount() { return bridgePurchaseOrderCount; }
        public String getBridgeLogisticsNos() { return bridgeLogisticsNos; }
        public String getBridgePurchaseOrderNos() { return bridgePurchaseOrderNos; }
        public String getAttributionStatus() { return attributionStatus; }
        public String getBlocker() { return blocker; }
        public boolean isFifoReady() { return fifoReady; }
    }

    public static class BatchAttributionLineRow {
        public String lineType;
        public String sourceType;
        public String sourceId;
        public String purchaseOrderNo;
        public LocalDateTime purchaseBatchTime;
        public BigDecimal purchaseQuantity;
        public BigDecimal soldQuantity;
        public BigDecimal stockQuantity;
        public BigDecimal purchaseCostCny;
        public String logisticsBatchNo;
        public String asnNo;
        public String lineStatus;
        public String evidenceText;
    }

    public static class BatchAttributionCandidateRow {
        public String sourceType;
        public String sourceId;
        public String purchaseOrderNo;
        public LocalDateTime purchaseBatchTime;
        public BigDecimal purchaseQuantity;
        public BigDecimal purchaseCostCny;
        public String logisticsBatchNo;
        public String asnNo;
        public String evidenceText;
    }

    public static class BatchAttributionCurrentStockRow {
        public String partnerSku;
        public BigDecimal stockQuantity;
        public BigDecimal sellableStockQuantity;
    }

    public static class BatchAttributionSkuDetailView {
        private String partnerSku;
        private BigDecimal stockQuantity = BigDecimal.ZERO;
        private BigDecimal sellableStockQuantity = BigDecimal.ZERO;
        private BigDecimal soldQuantity = BigDecimal.ZERO;
        private List<BatchAttributionLineView> lines = List.of();

        public BatchAttributionSkuDetailView() {
        }

        public BatchAttributionSkuDetailView(
                String partnerSku,
                BigDecimal stockQuantity,
                BigDecimal sellableStockQuantity,
                BigDecimal soldQuantity,
                List<BatchAttributionLineView> lines
        ) {
            this.partnerSku = partnerSku;
            this.stockQuantity = value(stockQuantity);
            this.sellableStockQuantity = value(sellableStockQuantity);
            this.soldQuantity = value(soldQuantity);
            this.lines = lines == null ? List.of() : lines;
        }

        public String getPartnerSku() { return partnerSku; }
        public BigDecimal getStockQuantity() { return stockQuantity; }
        public BigDecimal getSellableStockQuantity() { return sellableStockQuantity; }
        public BigDecimal getSoldQuantity() { return soldQuantity; }
        public List<BatchAttributionLineView> getLines() { return lines; }
    }

    public static class BatchAttributionLineView {
        private String lineType;
        private String sourceType;
        private String sourceId;
        private String purchaseOrderNo;
        private LocalDateTime purchaseBatchTime;
        private BigDecimal purchaseQuantity = BigDecimal.ZERO;
        private BigDecimal soldQuantity = BigDecimal.ZERO;
        private BigDecimal stockQuantity = BigDecimal.ZERO;
        private BigDecimal purchaseCostCny;
        private String logisticsBatchNo;
        private String asnNo;
        private String lineStatus;
        private String evidenceText;

        public static BatchAttributionLineView from(BatchAttributionLineRow row) {
            BatchAttributionLineView view = new BatchAttributionLineView();
            if (row == null) {
                return view;
            }
            view.lineType = row.lineType;
            view.sourceType = row.sourceType;
            view.sourceId = row.sourceId;
            view.purchaseOrderNo = row.purchaseOrderNo;
            view.purchaseBatchTime = row.purchaseBatchTime;
            view.purchaseQuantity = value(row.purchaseQuantity);
            view.soldQuantity = value(row.soldQuantity);
            view.stockQuantity = value(row.stockQuantity);
            view.purchaseCostCny = row.purchaseCostCny;
            view.logisticsBatchNo = row.logisticsBatchNo;
            view.asnNo = row.asnNo;
            view.lineStatus = row.lineStatus;
            view.evidenceText = row.evidenceText;
            return view;
        }

        public static BatchAttributionLineView currentStockTail(
                BatchAttributionCandidateRow candidate,
                BigDecimal stockQuantity,
                BigDecimal purchaseCostCny,
                String lineStatus
        ) {
            BatchAttributionLineView view = new BatchAttributionLineView();
            view.lineType = "current_stock_tail";
            view.sourceType = candidate == null ? null : candidate.sourceType;
            view.sourceId = candidate == null ? null : candidate.sourceId;
            view.purchaseOrderNo = candidate == null ? null : candidate.purchaseOrderNo;
            view.purchaseBatchTime = candidate == null ? null : candidate.purchaseBatchTime;
            view.purchaseQuantity = candidate == null ? BigDecimal.ZERO : value(candidate.purchaseQuantity);
            view.stockQuantity = value(stockQuantity);
            view.purchaseCostCny = purchaseCostCny;
            view.logisticsBatchNo = candidate == null ? null : candidate.logisticsBatchNo;
            view.asnNo = candidate == null ? null : candidate.asnNo;
            view.lineStatus = lineStatus;
            view.evidenceText = candidate == null ? null : candidate.evidenceText;
            return view;
        }

        public static BatchAttributionLineView currentStockTail(BatchAttributionCurrentStockRow stock) {
            BatchAttributionLineView view = new BatchAttributionLineView();
            view.lineType = "current_stock_tail";
            view.sourceId = "OFFICIAL_WAREHOUSE_CURRENT_STOCK:" + (stock == null ? "" : stock.partnerSku);
            view.stockQuantity = stock == null ? BigDecimal.ZERO : value(stock.sellableStockQuantity);
            view.lineStatus = "stock_waiting_fifo_attribution";
            view.evidenceText = "official_warehouse_inventory_snapshot_line current sellable stock";
            return view;
        }

        public String getLineType() { return lineType; }
        public String getSourceType() { return sourceType; }
        public String getSourceId() { return sourceId; }
        public String getPurchaseOrderNo() { return purchaseOrderNo; }
        public LocalDateTime getPurchaseBatchTime() { return purchaseBatchTime; }
        public BigDecimal getPurchaseQuantity() { return purchaseQuantity; }
        public BigDecimal getSoldQuantity() { return soldQuantity; }
        public BigDecimal getStockQuantity() { return stockQuantity; }
        public BigDecimal getPurchaseCostCny() { return purchaseCostCny; }
        public String getLogisticsBatchNo() { return logisticsBatchNo; }
        public String getAsnNo() { return asnNo; }
        public String getLineStatus() { return lineStatus; }
        public String getEvidenceText() { return evidenceText; }
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int count(Integer value) {
        return value == null ? 0 : value;
    }
}
