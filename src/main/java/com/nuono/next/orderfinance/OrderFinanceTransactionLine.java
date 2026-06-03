package com.nuono.next.orderfinance;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OrderFinanceTransactionLine {
    private Long id;
    private String sourceBatchId;
    private String referenceNr;
    private String orderNr;
    private String itemNr;
    private LocalDate orderDate;
    private LocalDate transactionDate;
    private String title;
    private String sku;
    private String partnerSku;
    private String transactionType;
    private String currency;
    private BigDecimal netProceeds = BigDecimal.ZERO;
    private BigDecimal referralFee = BigDecimal.ZERO;
    private BigDecimal fulfillmentLogisticsFee = BigDecimal.ZERO;
    private BigDecimal shippingCredits = BigDecimal.ZERO;
    private BigDecimal otherOrderFee = BigDecimal.ZERO;
    private BigDecimal orderSubsidies = BigDecimal.ZERO;
    private BigDecimal nonOrderFees = BigDecimal.ZERO;
    private BigDecimal nonOrderSubsidies = BigDecimal.ZERO;
    private BigDecimal others = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceBatchId() { return sourceBatchId; }
    public void setSourceBatchId(String sourceBatchId) { this.sourceBatchId = sourceBatchId; }
    public String getReferenceNr() { return referenceNr; }
    public void setReferenceNr(String referenceNr) { this.referenceNr = referenceNr; }
    public String getOrderNr() { return orderNr; }
    public void setOrderNr(String orderNr) { this.orderNr = orderNr; }
    public String getItemNr() { return itemNr; }
    public void setItemNr(String itemNr) { this.itemNr = itemNr; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getPartnerSku() { return partnerSku; }
    public void setPartnerSku(String partnerSku) { this.partnerSku = partnerSku; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getNetProceeds() { return netProceeds; }
    public void setNetProceeds(BigDecimal netProceeds) { this.netProceeds = valueOrZero(netProceeds); }
    public BigDecimal getReferralFee() { return referralFee; }
    public void setReferralFee(BigDecimal referralFee) { this.referralFee = valueOrZero(referralFee); }
    public BigDecimal getFulfillmentLogisticsFee() { return fulfillmentLogisticsFee; }
    public void setFulfillmentLogisticsFee(BigDecimal fulfillmentLogisticsFee) { this.fulfillmentLogisticsFee = valueOrZero(fulfillmentLogisticsFee); }
    public BigDecimal getShippingCredits() { return shippingCredits; }
    public void setShippingCredits(BigDecimal shippingCredits) { this.shippingCredits = valueOrZero(shippingCredits); }
    public BigDecimal getOtherOrderFee() { return otherOrderFee; }
    public void setOtherOrderFee(BigDecimal otherOrderFee) { this.otherOrderFee = valueOrZero(otherOrderFee); }
    public BigDecimal getOrderSubsidies() { return orderSubsidies; }
    public void setOrderSubsidies(BigDecimal orderSubsidies) { this.orderSubsidies = valueOrZero(orderSubsidies); }
    public BigDecimal getNonOrderFees() { return nonOrderFees; }
    public void setNonOrderFees(BigDecimal nonOrderFees) { this.nonOrderFees = valueOrZero(nonOrderFees); }
    public BigDecimal getNonOrderSubsidies() { return nonOrderSubsidies; }
    public void setNonOrderSubsidies(BigDecimal nonOrderSubsidies) { this.nonOrderSubsidies = valueOrZero(nonOrderSubsidies); }
    public BigDecimal getOthers() { return others; }
    public void setOthers(BigDecimal others) { this.others = valueOrZero(others); }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = valueOrZero(totalAmount); }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
