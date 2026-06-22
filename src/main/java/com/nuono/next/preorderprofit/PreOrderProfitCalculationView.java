package com.nuono.next.preorderprofit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PreOrderProfitCalculationView {
    private String status;
    private String currency;
    private BigDecimal taxRate;
    private BigDecimal exchangeRate;
    private final List<String> missingFields = new ArrayList<>();
    private final List<String> missingRules = new ArrayList<>();
    private final List<String> formulaIssues = new ArrayList<>();
    private BigDecimal purchaseCost;
    private BigDecimal domesticLogisticsFeeRmb;
    private BigDecimal domesticLogisticsFee;
    private BigDecimal volumeWeightKg;
    private BigDecimal billingWeightKg;
    private BigDecimal firstLegLogisticsFeeRmb;
    private BigDecimal firstLegLogisticsFee;
    private BigDecimal commissionBase;
    private BigDecimal commissionFeeTaxIncluded;
    private BigDecimal fulfillmentFeeBase;
    private BigDecimal fulfillmentFeeTaxIncluded;
    private BigDecimal totalCost;
    private BigDecimal estimatedProfit;
    private BigDecimal estimatedMarginRatePct;
    private BigDecimal breakEvenSalePrice;
    private BigDecimal targetMarginSalePrice;
    private final List<CostLine> costLines = new ArrayList<>();

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

    public List<String> getMissingRules() {
        return missingRules;
    }

    public List<String> getFormulaIssues() {
        return formulaIssues;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public void setPurchaseCost(BigDecimal purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    public BigDecimal getDomesticLogisticsFeeRmb() {
        return domesticLogisticsFeeRmb;
    }

    public void setDomesticLogisticsFeeRmb(BigDecimal domesticLogisticsFeeRmb) {
        this.domesticLogisticsFeeRmb = domesticLogisticsFeeRmb;
    }

    public BigDecimal getDomesticLogisticsFee() {
        return domesticLogisticsFee;
    }

    public void setDomesticLogisticsFee(BigDecimal domesticLogisticsFee) {
        this.domesticLogisticsFee = domesticLogisticsFee;
    }

    public BigDecimal getVolumeWeightKg() {
        return volumeWeightKg;
    }

    public void setVolumeWeightKg(BigDecimal volumeWeightKg) {
        this.volumeWeightKg = volumeWeightKg;
    }

    public BigDecimal getBillingWeightKg() {
        return billingWeightKg;
    }

    public void setBillingWeightKg(BigDecimal billingWeightKg) {
        this.billingWeightKg = billingWeightKg;
    }

    public BigDecimal getFirstLegLogisticsFeeRmb() {
        return firstLegLogisticsFeeRmb;
    }

    public void setFirstLegLogisticsFeeRmb(BigDecimal firstLegLogisticsFeeRmb) {
        this.firstLegLogisticsFeeRmb = firstLegLogisticsFeeRmb;
    }

    public BigDecimal getFirstLegLogisticsFee() {
        return firstLegLogisticsFee;
    }

    public void setFirstLegLogisticsFee(BigDecimal firstLegLogisticsFee) {
        this.firstLegLogisticsFee = firstLegLogisticsFee;
    }

    public BigDecimal getCommissionBase() {
        return commissionBase;
    }

    public void setCommissionBase(BigDecimal commissionBase) {
        this.commissionBase = commissionBase;
    }

    public BigDecimal getCommissionFeeTaxIncluded() {
        return commissionFeeTaxIncluded;
    }

    public void setCommissionFeeTaxIncluded(BigDecimal commissionFeeTaxIncluded) {
        this.commissionFeeTaxIncluded = commissionFeeTaxIncluded;
    }

    public BigDecimal getFulfillmentFeeBase() {
        return fulfillmentFeeBase;
    }

    public void setFulfillmentFeeBase(BigDecimal fulfillmentFeeBase) {
        this.fulfillmentFeeBase = fulfillmentFeeBase;
    }

    public BigDecimal getFulfillmentFeeTaxIncluded() {
        return fulfillmentFeeTaxIncluded;
    }

    public void setFulfillmentFeeTaxIncluded(BigDecimal fulfillmentFeeTaxIncluded) {
        this.fulfillmentFeeTaxIncluded = fulfillmentFeeTaxIncluded;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getEstimatedProfit() {
        return estimatedProfit;
    }

    public void setEstimatedProfit(BigDecimal estimatedProfit) {
        this.estimatedProfit = estimatedProfit;
    }

    public BigDecimal getEstimatedMarginRatePct() {
        return estimatedMarginRatePct;
    }

    public void setEstimatedMarginRatePct(BigDecimal estimatedMarginRatePct) {
        this.estimatedMarginRatePct = estimatedMarginRatePct;
    }

    public BigDecimal getBreakEvenSalePrice() {
        return breakEvenSalePrice;
    }

    public void setBreakEvenSalePrice(BigDecimal breakEvenSalePrice) {
        this.breakEvenSalePrice = breakEvenSalePrice;
    }

    public BigDecimal getTargetMarginSalePrice() {
        return targetMarginSalePrice;
    }

    public void setTargetMarginSalePrice(BigDecimal targetMarginSalePrice) {
        this.targetMarginSalePrice = targetMarginSalePrice;
    }

    public List<CostLine> getCostLines() {
        return costLines;
    }

    public static class CostLine {
        private String key;
        private String label;
        private BigDecimal amount;
        private String currency;
        private String note;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }
}
