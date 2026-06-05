package com.nuono.next.procurement.aliorder;

import java.util.ArrayList;
import java.util.List;

public interface Ali1688HistoricalOrderProvider {

    default Page fetchFirstPage(Ali1688HistoricalOrderAuthorizationRow authorization) {
        return fetchPage(authorization, null);
    }

    Page fetchPage(Ali1688HistoricalOrderAuthorizationRow authorization, String cursor);

    class Page {
        private final List<OrderSnapshot> orders;
        private String nextCursor;
        private boolean hasMore;
        private int progressPercent = 100;
        private String failureCode;
        private String failureMessage;
        private boolean retryableFailure;

        public Page(List<OrderSnapshot> orders) {
            this.orders = orders == null ? List.of() : orders;
        }

        public List<OrderSnapshot> getOrders() {
            return orders;
        }

        public String getNextCursor() {
            return nextCursor;
        }

        public void setNextCursor(String nextCursor) {
            this.nextCursor = nextCursor;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public void setHasMore(boolean hasMore) {
            this.hasMore = hasMore;
        }

        public int getProgressPercent() {
            return progressPercent;
        }

        public void setProgressPercent(int progressPercent) {
            this.progressPercent = progressPercent;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        public boolean isRetryableFailure() {
            return retryableFailure;
        }

        public void setRetryableFailure(boolean retryableFailure) {
            this.retryableFailure = retryableFailure;
        }

        public boolean hasFailure() {
            return failureCode != null && !failureCode.isBlank();
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

        public List<OrderItemSnapshot> getItems() {
            return items;
        }

        public void setItems(List<OrderItemSnapshot> items) {
            this.items = items == null ? List.of() : items;
        }
    }

    class OrderItemSnapshot {
        private String offerId;
        private String skuId;
        private String title;
        private String skuText;
        private String modelText;
        private String productCode;
        private String singleProductCode;
        private Integer quantity;
        private String unit;
        private String unitPriceText;
        private String amountText;
        private String imageUrl;
        private String logisticsCompany;
        private String trackingNo;
        private String rawSnapshotJson;

        public String getOfferId() {
            return offerId;
        }

        public void setOfferId(String offerId) {
            this.offerId = offerId;
        }

        public String getSkuId() {
            return skuId;
        }

        public void setSkuId(String skuId) {
            this.skuId = skuId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSkuText() {
            return skuText;
        }

        public void setSkuText(String skuText) {
            this.skuText = skuText;
        }

        public String getModelText() {
            return modelText;
        }

        public void setModelText(String modelText) {
            this.modelText = modelText;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }

        public String getSingleProductCode() {
            return singleProductCode;
        }

        public void setSingleProductCode(String singleProductCode) {
            this.singleProductCode = singleProductCode;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public String getUnitPriceText() {
            return unitPriceText;
        }

        public void setUnitPriceText(String unitPriceText) {
            this.unitPriceText = unitPriceText;
        }

        public String getAmountText() {
            return amountText;
        }

        public void setAmountText(String amountText) {
            this.amountText = amountText;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getLogisticsCompany() {
            return logisticsCompany;
        }

        public void setLogisticsCompany(String logisticsCompany) {
            this.logisticsCompany = logisticsCompany;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public void setTrackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
        }

        public String getRawSnapshotJson() {
            return rawSnapshotJson;
        }

        public void setRawSnapshotJson(String rawSnapshotJson) {
            this.rawSnapshotJson = rawSnapshotJson;
        }
    }
}
