package com.nuono.next.orderfinance;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OrderFinanceActualCommissionSnapshot {
    private String partnerSku;
    private String sku;
    private String currency;
    private long sampleCount;
    private long transactionRowCount;
    private BigDecimal totalCommissionAmount = BigDecimal.ZERO;
    private BigDecimal averageCommissionAmount;
    private BigDecimal latestCommissionAmount;
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
    public BigDecimal getTotalCommissionAmount() { return totalCommissionAmount; }
    public void setTotalCommissionAmount(BigDecimal totalCommissionAmount) { this.totalCommissionAmount = valueOrZero(totalCommissionAmount); }
    public BigDecimal getAverageCommissionAmount() { return averageCommissionAmount; }
    public void setAverageCommissionAmount(BigDecimal averageCommissionAmount) { this.averageCommissionAmount = averageCommissionAmount; }
    public BigDecimal getLatestCommissionAmount() { return latestCommissionAmount; }
    public void setLatestCommissionAmount(BigDecimal latestCommissionAmount) { this.latestCommissionAmount = latestCommissionAmount; }
    public LocalDate getLatestTransactionDate() { return latestTransactionDate; }
    public void setLatestTransactionDate(LocalDate latestTransactionDate) { this.latestTransactionDate = latestTransactionDate; }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
