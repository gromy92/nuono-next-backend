package com.nuono.next.orderfinance;

import java.math.BigDecimal;
import java.time.LocalDate;

public class NoonFinanceTransactionFact {
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String sourceBatchId;
    private final String fileDigestSha256;
    private final String rowHash;
    private final String contractCode;
    private final String contractTitle;
    private final String referenceNr;
    private final String orderNr;
    private final String itemNr;
    private final LocalDate orderDate;
    private final LocalDate transactionDate;
    private final String title;
    private final String sku;
    private final String partnerSku;
    private final String transactionType;
    private final String currency;
    private final BigDecimal netProceeds;
    private final BigDecimal referralFeeIncludingVat;
    private final BigDecimal fulfillmentLogisticsFeesIncludingVat;
    private final BigDecimal shippingCreditsIncludingVat;
    private final BigDecimal otherOrderFeesIncludingVat;
    private final BigDecimal orderSubsidiesIncludingVat;
    private final BigDecimal nonOrderFeesIncludingVat;
    private final BigDecimal nonOrderSubsidiesIncludingVat;
    private final BigDecimal othersIncludingVat;
    private final BigDecimal totalAmount;
    private final LocalDate reportDateFrom;
    private final LocalDate reportDateTo;

    public NoonFinanceTransactionFact(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String sourceBatchId,
            String fileDigestSha256,
            String rowHash,
            String contractCode,
            String contractTitle,
            String referenceNr,
            String orderNr,
            String itemNr,
            LocalDate orderDate,
            LocalDate transactionDate,
            String title,
            String sku,
            String partnerSku,
            String transactionType,
            String currency,
            BigDecimal netProceeds,
            BigDecimal referralFeeIncludingVat,
            BigDecimal fulfillmentLogisticsFeesIncludingVat,
            BigDecimal shippingCreditsIncludingVat,
            BigDecimal otherOrderFeesIncludingVat,
            BigDecimal orderSubsidiesIncludingVat,
            BigDecimal nonOrderFeesIncludingVat,
            BigDecimal nonOrderSubsidiesIncludingVat,
            BigDecimal othersIncludingVat,
            BigDecimal totalAmount,
            LocalDate reportDateFrom,
            LocalDate reportDateTo
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.sourceBatchId = sourceBatchId;
        this.fileDigestSha256 = fileDigestSha256;
        this.rowHash = rowHash;
        this.contractCode = contractCode;
        this.contractTitle = contractTitle;
        this.referenceNr = referenceNr;
        this.orderNr = orderNr;
        this.itemNr = itemNr;
        this.orderDate = orderDate;
        this.transactionDate = transactionDate;
        this.title = title;
        this.sku = sku;
        this.partnerSku = partnerSku;
        this.transactionType = transactionType;
        this.currency = currency;
        this.netProceeds = valueOrZero(netProceeds);
        this.referralFeeIncludingVat = valueOrZero(referralFeeIncludingVat);
        this.fulfillmentLogisticsFeesIncludingVat = valueOrZero(fulfillmentLogisticsFeesIncludingVat);
        this.shippingCreditsIncludingVat = valueOrZero(shippingCreditsIncludingVat);
        this.otherOrderFeesIncludingVat = valueOrZero(otherOrderFeesIncludingVat);
        this.orderSubsidiesIncludingVat = valueOrZero(orderSubsidiesIncludingVat);
        this.nonOrderFeesIncludingVat = valueOrZero(nonOrderFeesIncludingVat);
        this.nonOrderSubsidiesIncludingVat = valueOrZero(nonOrderSubsidiesIncludingVat);
        this.othersIncludingVat = valueOrZero(othersIncludingVat);
        this.totalAmount = valueOrZero(totalAmount);
        this.reportDateFrom = reportDateFrom;
        this.reportDateTo = reportDateTo;
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getSourceBatchId() { return sourceBatchId; }
    public String getFileDigestSha256() { return fileDigestSha256; }
    public String getRowHash() { return rowHash; }
    public String getContractCode() { return contractCode; }
    public String getContractTitle() { return contractTitle; }
    public String getReferenceNr() { return referenceNr; }
    public String getOrderNr() { return orderNr; }
    public String getItemNr() { return itemNr; }
    public LocalDate getOrderDate() { return orderDate; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getTitle() { return title; }
    public String getSku() { return sku; }
    public String getPartnerSku() { return partnerSku; }
    public String getTransactionType() { return transactionType; }
    public String getCurrency() { return currency; }
    public BigDecimal getNetProceeds() { return netProceeds; }
    public BigDecimal getReferralFeeIncludingVat() { return referralFeeIncludingVat; }
    public BigDecimal getFulfillmentLogisticsFeesIncludingVat() { return fulfillmentLogisticsFeesIncludingVat; }
    public BigDecimal getShippingCreditsIncludingVat() { return shippingCreditsIncludingVat; }
    public BigDecimal getOtherOrderFeesIncludingVat() { return otherOrderFeesIncludingVat; }
    public BigDecimal getOrderSubsidiesIncludingVat() { return orderSubsidiesIncludingVat; }
    public BigDecimal getNonOrderFeesIncludingVat() { return nonOrderFeesIncludingVat; }
    public BigDecimal getNonOrderSubsidiesIncludingVat() { return nonOrderSubsidiesIncludingVat; }
    public BigDecimal getOthersIncludingVat() { return othersIncludingVat; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDate getReportDateFrom() { return reportDateFrom; }
    public LocalDate getReportDateTo() { return reportDateTo; }
}
