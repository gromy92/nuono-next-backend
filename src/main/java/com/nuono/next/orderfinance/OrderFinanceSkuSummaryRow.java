package com.nuono.next.orderfinance;

import java.math.BigDecimal;

public class OrderFinanceSkuSummaryRow {
    private String partnerSku;
    private String sku;
    private String title;
    private String imageUrl;
    private String currency;
    private long orderCount;
    private long itemCount;
    private long transactionRowCount;
    private long orderUpdateRowCount;
    private BigDecimal netProceeds = BigDecimal.ZERO;
    private BigDecimal referralFee = BigDecimal.ZERO;
    private BigDecimal fulfillmentLogisticsFee = BigDecimal.ZERO;
    private BigDecimal otherOrderFee = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal avgTotalPerOrder;
    private BigDecimal avgFulfillmentFeePerItem;
    private BigDecimal feeRate;
    private boolean missingPartnerSku;

    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public long getOrderCount() { return orderCount; }
    public void setOrderCount(long orderCount) { this.orderCount = orderCount; }
    public long getItemCount() { return itemCount; }
    public void setItemCount(long itemCount) { this.itemCount = itemCount; }
    public long getTransactionRowCount() { return transactionRowCount; }
    public void setTransactionRowCount(long transactionRowCount) { this.transactionRowCount = transactionRowCount; }
    public long getOrderUpdateRowCount() { return orderUpdateRowCount; }
    public void setOrderUpdateRowCount(long orderUpdateRowCount) { this.orderUpdateRowCount = orderUpdateRowCount; }
    public BigDecimal getNetProceeds() { return netProceeds; }
    public void setNetProceeds(BigDecimal netProceeds) { this.netProceeds = valueOrZero(netProceeds); }
    public BigDecimal getReferralFee() { return referralFee; }
    public void setReferralFee(BigDecimal referralFee) { this.referralFee = valueOrZero(referralFee); }
    public BigDecimal getFulfillmentLogisticsFee() { return fulfillmentLogisticsFee; }
    public void setFulfillmentLogisticsFee(BigDecimal fulfillmentLogisticsFee) { this.fulfillmentLogisticsFee = valueOrZero(fulfillmentLogisticsFee); }
    public BigDecimal getOtherOrderFee() { return otherOrderFee; }
    public void setOtherOrderFee(BigDecimal otherOrderFee) { this.otherOrderFee = valueOrZero(otherOrderFee); }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = valueOrZero(totalAmount); }
    public BigDecimal getAvgTotalPerOrder() { return avgTotalPerOrder; }
    public void setAvgTotalPerOrder(BigDecimal avgTotalPerOrder) { this.avgTotalPerOrder = avgTotalPerOrder; }
    public BigDecimal getAvgFulfillmentFeePerItem() { return avgFulfillmentFeePerItem; }
    public void setAvgFulfillmentFeePerItem(BigDecimal avgFulfillmentFeePerItem) { this.avgFulfillmentFeePerItem = avgFulfillmentFeePerItem; }
    public BigDecimal getFeeRate() { return feeRate; }
    public void setFeeRate(BigDecimal feeRate) { this.feeRate = feeRate; }
    public boolean isMissingPartnerSku() { return missingPartnerSku; }
    public void setMissingPartnerSku(boolean missingPartnerSku) { this.missingPartnerSku = missingPartnerSku; }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
