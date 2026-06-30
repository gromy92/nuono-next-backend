package com.nuono.next.orderfinance;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OrderFinanceActualOutboundFeeSnapshot {
    private String partnerSku;
    private String sku;
    private String currency;
    private long sampleCount;
    private long transactionRowCount;
    private BigDecimal totalFeeAmount = BigDecimal.ZERO;
    private BigDecimal averageFeeAmount;
    private BigDecimal latestFeeAmount;
    private LocalDate latestTransactionDate;

    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public long getSampleCount() { return sampleCount; }
    public void setSampleCount(long sampleCount) { this.sampleCount = sampleCount; }
    public long getTransactionRowCount() { return transactionRowCount; }
    public void setTransactionRowCount(long transactionRowCount) { this.transactionRowCount = transactionRowCount; }
    public BigDecimal getTotalFeeAmount() { return totalFeeAmount; }
    public void setTotalFeeAmount(BigDecimal totalFeeAmount) { this.totalFeeAmount = valueOrZero(totalFeeAmount); }
    public BigDecimal getAverageFeeAmount() { return averageFeeAmount; }
    public void setAverageFeeAmount(BigDecimal averageFeeAmount) { this.averageFeeAmount = averageFeeAmount; }
    public BigDecimal getLatestFeeAmount() { return latestFeeAmount; }
    public void setLatestFeeAmount(BigDecimal latestFeeAmount) { this.latestFeeAmount = latestFeeAmount; }
    public LocalDate getLatestTransactionDate() { return latestTransactionDate; }
    public void setLatestTransactionDate(LocalDate latestTransactionDate) { this.latestTransactionDate = latestTransactionDate; }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
