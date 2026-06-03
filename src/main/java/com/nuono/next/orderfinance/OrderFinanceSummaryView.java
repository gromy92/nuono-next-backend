package com.nuono.next.orderfinance;

import java.math.BigDecimal;

public class OrderFinanceSummaryView {
    private String currency;
    private boolean mixedCurrency;
    private long orderCount;
    private long itemCount;
    private long transactionRowCount;
    private long orderUpdateRowCount;
    private BigDecimal netProceeds = BigDecimal.ZERO;
    private BigDecimal referralFee = BigDecimal.ZERO;
    private BigDecimal fulfillmentLogisticsFee = BigDecimal.ZERO;
    private BigDecimal otherOrderFee = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;

    public OrderFinanceSummaryView() {
    }

    public OrderFinanceSummaryView(String currency, boolean mixedCurrency) {
        this.currency = currency;
        this.mixedCurrency = mixedCurrency;
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isMixedCurrency() { return mixedCurrency; }
    public void setMixedCurrency(boolean mixedCurrency) { this.mixedCurrency = mixedCurrency; }
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

    public void addCounts(OrderFinanceSummaryView source) {
        if (source == null) {
            return;
        }
        orderCount += source.getOrderCount();
        itemCount += source.getItemCount();
        transactionRowCount += source.getTransactionRowCount();
        orderUpdateRowCount += source.getOrderUpdateRowCount();
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
