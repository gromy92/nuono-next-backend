package com.nuono.next.intransit;

import com.nuono.next.product.ProductImageUrlSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class InTransitSuperSearchRecords {

    private InTransitSuperSearchRecords() {
    }

    public static class SuperSearchView {
        private String keyword;
        private boolean includeHistory;
        private int totalCount;
        private List<SuperSearchItemView> items = Collections.emptyList();

        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public boolean isIncludeHistory() { return includeHistory; }
        public void setIncludeHistory(boolean includeHistory) { this.includeHistory = includeHistory; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public List<SuperSearchItemView> getItems() { return items; }
        public void setItems(List<SuperSearchItemView> items) {
            this.items = items == null ? Collections.emptyList() : items;
            this.totalCount = this.items.size();
        }

        public static SuperSearchView empty(String keyword, boolean includeHistory) {
            SuperSearchView view = new SuperSearchView();
            view.setKeyword(keyword);
            view.setIncludeHistory(includeHistory);
            view.setItems(Collections.emptyList());
            return view;
        }

        public static SuperSearchView from(String keyword, boolean includeHistory, List<SuperSearchItemRow> rows) {
            SuperSearchView view = new SuperSearchView();
            view.setKeyword(keyword);
            view.setIncludeHistory(includeHistory);
            view.setItems((rows == null ? Collections.<SuperSearchItemRow>emptyList() : rows).stream()
                    .map(SuperSearchItemView::from)
                    .collect(Collectors.toList()));
            return view;
        }
    }

    public static class SuperSearchItemView {
        private String psku;
        private String productName;
        private String productTitle;
        private String productTitleCn;
        private String productImageUrl;
        private Long batchId;
        private String batchReferenceNo;
        private String rawForwarderName;
        private String standardForwarderName;
        private String transportMode;
        private String batchStatus;
        private String targetStoreCode;
        private String targetSiteCode;
        private String targetWarehouseName;
        private LocalDateTime sourceCreatedAt;
        private LocalDateTime domesticReceivedAt;
        private LocalDateTime latestNodeHappenedAt;
        private String latestNodeStatus;
        private String latestNodeDescription;
        private LocalDate etaDate;
        private Integer boxCount;
        private Integer shippedQuantityTotal;
        private Integer receivedQuantityTotal;
        private Integer remainingQuantityTotal;

        public static SuperSearchItemView from(SuperSearchItemRow row) {
            SuperSearchItemView view = new SuperSearchItemView();
            view.setPsku(row.getPsku());
            view.setProductName(row.getProductName());
            view.setProductTitle(row.getProductTitle());
            view.setProductTitleCn(row.getProductTitleCn());
            view.setProductImageUrl(ProductImageUrlSupport.normalize(row.getProductImageUrl()));
            view.setBatchId(row.getBatchId());
            view.setBatchReferenceNo(row.getBatchReferenceNo());
            view.setRawForwarderName(row.getRawForwarderName());
            view.setStandardForwarderName(row.getStandardForwarderName());
            view.setTransportMode(row.getTransportMode());
            view.setBatchStatus(row.getBatchStatus());
            view.setTargetStoreCode(row.getTargetStoreCode());
            view.setTargetSiteCode(row.getTargetSiteCode());
            view.setTargetWarehouseName(row.getTargetWarehouseName());
            view.setSourceCreatedAt(row.getSourceCreatedAt());
            view.setDomesticReceivedAt(row.getDomesticReceivedAt());
            view.setLatestNodeHappenedAt(row.getLatestNodeHappenedAt());
            view.setLatestNodeStatus(row.getLatestNodeStatus());
            view.setLatestNodeDescription(row.getLatestNodeDescription());
            view.setEtaDate(row.getEtaDate());
            view.setBoxCount(row.getBoxCount());
            view.setShippedQuantityTotal(row.getShippedQuantityTotal());
            view.setReceivedQuantityTotal(row.getReceivedQuantityTotal());
            view.setRemainingQuantityTotal(row.getRemainingQuantityTotal());
            return view;
        }

        public String getPsku() { return psku; }
        public void setPsku(String psku) { this.psku = psku; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getProductTitle() { return productTitle; }
        public void setProductTitle(String productTitle) { this.productTitle = productTitle; }
        public String getProductTitleCn() { return productTitleCn; }
        public void setProductTitleCn(String productTitleCn) { this.productTitleCn = productTitleCn; }
        public String getProductImageUrl() { return productImageUrl; }
        public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getBatchReferenceNo() { return batchReferenceNo; }
        public void setBatchReferenceNo(String batchReferenceNo) { this.batchReferenceNo = batchReferenceNo; }
        public String getRawForwarderName() { return rawForwarderName; }
        public void setRawForwarderName(String rawForwarderName) { this.rawForwarderName = rawForwarderName; }
        public String getStandardForwarderName() { return standardForwarderName; }
        public void setStandardForwarderName(String standardForwarderName) { this.standardForwarderName = standardForwarderName; }
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
        public LocalDateTime getSourceCreatedAt() { return sourceCreatedAt; }
        public void setSourceCreatedAt(LocalDateTime sourceCreatedAt) { this.sourceCreatedAt = sourceCreatedAt; }
        public LocalDateTime getDomesticReceivedAt() { return domesticReceivedAt; }
        public void setDomesticReceivedAt(LocalDateTime domesticReceivedAt) { this.domesticReceivedAt = domesticReceivedAt; }
        public LocalDateTime getLatestNodeHappenedAt() { return latestNodeHappenedAt; }
        public void setLatestNodeHappenedAt(LocalDateTime latestNodeHappenedAt) { this.latestNodeHappenedAt = latestNodeHappenedAt; }
        public String getLatestNodeStatus() { return latestNodeStatus; }
        public void setLatestNodeStatus(String latestNodeStatus) { this.latestNodeStatus = latestNodeStatus; }
        public String getLatestNodeDescription() { return latestNodeDescription; }
        public void setLatestNodeDescription(String latestNodeDescription) { this.latestNodeDescription = latestNodeDescription; }
        public LocalDate getEtaDate() { return etaDate; }
        public void setEtaDate(LocalDate etaDate) { this.etaDate = etaDate; }
        public Integer getBoxCount() { return boxCount; }
        public void setBoxCount(Integer boxCount) { this.boxCount = boxCount; }
        public Integer getShippedQuantityTotal() { return shippedQuantityTotal; }
        public void setShippedQuantityTotal(Integer shippedQuantityTotal) { this.shippedQuantityTotal = shippedQuantityTotal; }
        public Integer getReceivedQuantityTotal() { return receivedQuantityTotal; }
        public void setReceivedQuantityTotal(Integer receivedQuantityTotal) { this.receivedQuantityTotal = receivedQuantityTotal; }
        public Integer getRemainingQuantityTotal() { return remainingQuantityTotal; }
        public void setRemainingQuantityTotal(Integer remainingQuantityTotal) { this.remainingQuantityTotal = remainingQuantityTotal; }
    }

    public static class SuperSearchItemRow extends SuperSearchItemView {
    }
}
