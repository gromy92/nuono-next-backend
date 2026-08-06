package com.nuono.next.procurement.aliorder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public interface Ali1688HistoricalOrderProvider {

    default Page fetchFirstPage(Ali1688HistoricalOrderAuthorizationRow authorization) {
        return fetchPage(authorization, null);
    }

    default Page fetchPage(Ali1688HistoricalOrderAuthorizationRow authorization, String cursor) {
        return fetchPage(Ali1688HistoricalOrderRequest.full(authorization, cursor));
    }

    /**
     * Legacy request-aware entry. Adapters may override it when manual refresh needs details in the
     * same call; DP-10 always calls {@link #fetchOrderList(Ali1688HistoricalOrderRequest)}.
     */
    default Page fetchPage(Ali1688HistoricalOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        return fetchOrderList(request);
    }

    /** Required DP-10 Seam. Implementations must consume mode, cursor, modifiedFrom and overlap. */
    Page fetchOrderList(Ali1688HistoricalOrderRequest request);

    /** Pure local contract value captured into the task checkpoint at initialization. */
    default int listPageSize() {
        return 20;
    }

    /** One official detail call for one exact order identity. */
    default DetailResult fetchOrderDetail(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            String providerOrderNo
    ) {
        return DetailResult.failure(
                Ali1688HistoricalOrderFailureCode.PROVIDER_NOT_CONFIGURED.getCode(),
                null
        );
    }

    /** Pure local check used to keep refresh and data HTTP calls in separate runtime advances. */
    default boolean requiresAuthorizationRefresh(
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        return false;
    }

    /** Performs at most one refresh HTTP call and persists its sanitized result. */
    default Ali1688HistoricalOrderAuthorizationRefreshResult refreshAuthorization(
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        return Ali1688HistoricalOrderAuthorizationRefreshResult.success();
    }

    enum SyncMode {
        FULL,
        INCREMENTAL
    }

    enum Partition {
        CURRENT(false),
        HISTORY(true);

        private final boolean historical;

        Partition(boolean historical) {
            this.historical = historical;
        }

        public boolean isHistorical() {
            return historical;
        }
    }

    enum DetailStatus {
        SUCCESS,
        NOT_FOUND,
        FAILURE
    }

    final class DetailResult extends Ali1688HistoricalOrderDetailResult {
        private DetailResult(DetailStatus status, OrderSnapshot order, String code, Duration retry) {
            super(status, order, code, retry);
        }
        public static DetailResult success(OrderSnapshot order) {
            if (order == null) throw new IllegalArgumentException("detail order is required");
            return new DetailResult(DetailStatus.SUCCESS, order, null, null);
        }
        public static DetailResult notFound() {
            return new DetailResult(DetailStatus.NOT_FOUND, null, null, null);
        }
        public static DetailResult failure(String failureCode, Duration retryAfter) {
            if (failureCode == null || failureCode.isBlank()) throw new IllegalArgumentException("code required");
            return new DetailResult(DetailStatus.FAILURE, null, failureCode, retryAfter);
        }
    }

    class Page extends Ali1688HistoricalOrderPage {
        public Page(List<OrderSnapshot> orders) {
            super(orders);
        }
    }

    class OrderSnapshot {
        private String providerOrderNo;
        private String orderTime;
        private String paidAt;
        private String buyerCompanyName;
        private String buyerMemberName;
        private String supplierName;
        private String sellerMemberName;
        private String goodsTotalText;
        private String freightText;
        private String adjustmentText;
        private String paidAmountText;
        private String amountText;
        private String currency;
        private String orderStatus;
        private String logisticsStatus;
        private String shipperName;
        private String originalUrl;
        private String receiverName;
        private String receiverPostalCode;
        private String receiverTelephone;
        private String receiverMobile;
        private String receiverPhone;
        private String receiverAddress;
        private String buyerRemark;
        private String supplierContact;
        private String initiatorLoginName;
        private String sourceBatchNo;
        private String downstreamOrderNo;
        private String rawSnapshotJson;
        private Instant providerModifiedAt;
        private List<OrderItemSnapshot> items = new ArrayList<>();

        public String getProviderOrderNo() {
            return providerOrderNo;
        }

        public void setProviderOrderNo(String providerOrderNo) {
            this.providerOrderNo = providerOrderNo;
        }

        public String getOrderTime() {
            return orderTime;
        }

        public void setOrderTime(String orderTime) {
            this.orderTime = orderTime;
        }

        public String getPaidAt() {
            return paidAt;
        }

        public void setPaidAt(String paidAt) {
            this.paidAt = paidAt;
        }

        public String getBuyerCompanyName() {
            return buyerCompanyName;
        }

        public void setBuyerCompanyName(String buyerCompanyName) {
            this.buyerCompanyName = buyerCompanyName;
        }

        public String getBuyerMemberName() {
            return buyerMemberName;
        }

        public void setBuyerMemberName(String buyerMemberName) {
            this.buyerMemberName = buyerMemberName;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getSellerMemberName() {
            return sellerMemberName;
        }

        public void setSellerMemberName(String sellerMemberName) {
            this.sellerMemberName = sellerMemberName;
        }

        public String getGoodsTotalText() {
            return goodsTotalText;
        }

        public void setGoodsTotalText(String goodsTotalText) {
            this.goodsTotalText = goodsTotalText;
        }

        public String getFreightText() {
            return freightText;
        }

        public void setFreightText(String freightText) {
            this.freightText = freightText;
        }

        public String getAdjustmentText() {
            return adjustmentText;
        }

        public void setAdjustmentText(String adjustmentText) {
            this.adjustmentText = adjustmentText;
        }

        public String getPaidAmountText() {
            return paidAmountText;
        }

        public void setPaidAmountText(String paidAmountText) {
            this.paidAmountText = paidAmountText;
        }

        public String getAmountText() {
            return amountText;
        }

        public void setAmountText(String amountText) {
            this.amountText = amountText;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getOrderStatus() {
            return orderStatus;
        }

        public void setOrderStatus(String orderStatus) {
            this.orderStatus = orderStatus;
        }

        public String getLogisticsStatus() {
            return logisticsStatus;
        }

        public void setLogisticsStatus(String logisticsStatus) {
            this.logisticsStatus = logisticsStatus;
        }

        public String getShipperName() {
            return shipperName;
        }

        public void setShipperName(String shipperName) {
            this.shipperName = shipperName;
        }

        public String getOriginalUrl() {
            return originalUrl;
        }

        public void setOriginalUrl(String originalUrl) {
            this.originalUrl = originalUrl;
        }

        public String getReceiverName() {
            return receiverName;
        }

        public void setReceiverName(String receiverName) {
            this.receiverName = receiverName;
        }

        public String getReceiverPostalCode() {
            return receiverPostalCode;
        }

        public void setReceiverPostalCode(String receiverPostalCode) {
            this.receiverPostalCode = receiverPostalCode;
        }

        public String getReceiverTelephone() {
            return receiverTelephone;
        }

        public void setReceiverTelephone(String receiverTelephone) {
            this.receiverTelephone = receiverTelephone;
        }

        public String getReceiverMobile() {
            return receiverMobile;
        }

        public void setReceiverMobile(String receiverMobile) {
            this.receiverMobile = receiverMobile;
        }

        public String getReceiverPhone() {
            return receiverPhone;
        }

        public void setReceiverPhone(String receiverPhone) {
            this.receiverPhone = receiverPhone;
        }

        public String getReceiverAddress() {
            return receiverAddress;
        }

        public void setReceiverAddress(String receiverAddress) {
            this.receiverAddress = receiverAddress;
        }

        public String getBuyerRemark() {
            return buyerRemark;
        }

        public void setBuyerRemark(String buyerRemark) {
            this.buyerRemark = buyerRemark;
        }

        public String getSupplierContact() {
            return supplierContact;
        }

        public void setSupplierContact(String supplierContact) {
            this.supplierContact = supplierContact;
        }

        public String getInitiatorLoginName() {
            return initiatorLoginName;
        }

        public void setInitiatorLoginName(String initiatorLoginName) {
            this.initiatorLoginName = initiatorLoginName;
        }

        public String getSourceBatchNo() {
            return sourceBatchNo;
        }

        public void setSourceBatchNo(String sourceBatchNo) {
            this.sourceBatchNo = sourceBatchNo;
        }

        public String getDownstreamOrderNo() {
            return downstreamOrderNo;
        }

        public void setDownstreamOrderNo(String downstreamOrderNo) {
            this.downstreamOrderNo = downstreamOrderNo;
        }

        public String getRawSnapshotJson() {
            return rawSnapshotJson;
        }

        public void setRawSnapshotJson(String rawSnapshotJson) {
            this.rawSnapshotJson = rawSnapshotJson;
        }

        public Instant getProviderModifiedAt() {
            return providerModifiedAt;
        }

        public void setProviderModifiedAt(Instant providerModifiedAt) {
            this.providerModifiedAt = providerModifiedAt;
        }

        public List<OrderItemSnapshot> getItems() {
            return items;
        }

        public void setItems(List<OrderItemSnapshot> items) {
            this.items = items == null ? List.of() : items;
        }
    }

    class OrderItemSnapshot extends Ali1688HistoricalOrderItemSnapshot {
    }
}
