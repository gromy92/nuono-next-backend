package com.nuono.next.profit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfitCalculationView {

    private boolean ready;

    private String message;

    private String title;

    private String site;

    private String marketCurrency;

    private BigDecimal salePrice;

    private BigDecimal purchasePrice;

    private BigDecimal exchangeRate;

    private BigDecimal lengthCm;

    private BigDecimal widthCm;

    private BigDecimal heightCm;

    private BigDecimal weightGrams;

    private BigDecimal cubeVolumeCbm;

    private BigDecimal dimensionalWeightGrams;

    private BigDecimal warehouseDeliveryFeeRmb;

    private BigDecimal airFirstLegFeeRmb;

    private BigDecimal oceanFirstLegFeeRmb;

    private List<String> notes = new ArrayList<>();

    private OfficialOutboundFeeView officialOutboundFee;

    private List<ScenarioView> scenarios = new ArrayList<>();

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getMarketCurrency() {
        return marketCurrency;
    }

    public void setMarketCurrency(String marketCurrency) {
        this.marketCurrency = marketCurrency;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public void setLengthCm(BigDecimal lengthCm) {
        this.lengthCm = lengthCm;
    }

    public BigDecimal getWidthCm() {
        return widthCm;
    }

    public void setWidthCm(BigDecimal widthCm) {
        this.widthCm = widthCm;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightGrams() {
        return weightGrams;
    }

    public void setWeightGrams(BigDecimal weightGrams) {
        this.weightGrams = weightGrams;
    }

    public BigDecimal getCubeVolumeCbm() {
        return cubeVolumeCbm;
    }

    public void setCubeVolumeCbm(BigDecimal cubeVolumeCbm) {
        this.cubeVolumeCbm = cubeVolumeCbm;
    }

    public BigDecimal getDimensionalWeightGrams() {
        return dimensionalWeightGrams;
    }

    public void setDimensionalWeightGrams(BigDecimal dimensionalWeightGrams) {
        this.dimensionalWeightGrams = dimensionalWeightGrams;
    }

    public BigDecimal getWarehouseDeliveryFeeRmb() {
        return warehouseDeliveryFeeRmb;
    }

    public void setWarehouseDeliveryFeeRmb(BigDecimal warehouseDeliveryFeeRmb) {
        this.warehouseDeliveryFeeRmb = warehouseDeliveryFeeRmb;
    }

    public BigDecimal getAirFirstLegFeeRmb() {
        return airFirstLegFeeRmb;
    }

    public void setAirFirstLegFeeRmb(BigDecimal airFirstLegFeeRmb) {
        this.airFirstLegFeeRmb = airFirstLegFeeRmb;
    }

    public BigDecimal getOceanFirstLegFeeRmb() {
        return oceanFirstLegFeeRmb;
    }

    public void setOceanFirstLegFeeRmb(BigDecimal oceanFirstLegFeeRmb) {
        this.oceanFirstLegFeeRmb = oceanFirstLegFeeRmb;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }

    public OfficialOutboundFeeView getOfficialOutboundFee() {
        return officialOutboundFee;
    }

    public void setOfficialOutboundFee(OfficialOutboundFeeView officialOutboundFee) {
        this.officialOutboundFee = officialOutboundFee;
    }

    public List<ScenarioView> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<ScenarioView> scenarios) {
        this.scenarios = scenarios;
    }

    public static class OfficialOutboundFeeView {

        private String status;

        private String failureCode;

        private String message;

        private BigDecimal feeAmount;

        private String currency;

        private String matchedClassificationName;

        private String matchedSlabNaturalKey;

        private Long sourceVersionId;

        private Map<String, Object> evidence = new LinkedHashMap<>();

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public BigDecimal getFeeAmount() {
            return feeAmount;
        }

        public void setFeeAmount(BigDecimal feeAmount) {
            this.feeAmount = feeAmount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getMatchedClassificationName() {
            return matchedClassificationName;
        }

        public void setMatchedClassificationName(String matchedClassificationName) {
            this.matchedClassificationName = matchedClassificationName;
        }

        public String getMatchedSlabNaturalKey() {
            return matchedSlabNaturalKey;
        }

        public void setMatchedSlabNaturalKey(String matchedSlabNaturalKey) {
            this.matchedSlabNaturalKey = matchedSlabNaturalKey;
        }

        public Long getSourceVersionId() {
            return sourceVersionId;
        }

        public void setSourceVersionId(Long sourceVersionId) {
            this.sourceVersionId = sourceVersionId;
        }

        public Map<String, Object> getEvidence() {
            return evidence;
        }

        public void setEvidence(Map<String, Object> evidence) {
            this.evidence = evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(evidence);
        }
    }

    public static class ScenarioView {

        private String code;

        private String label;

        private BigDecimal grossRevenueRmb;

        private BigDecimal commissionRatePct;

        private BigDecimal commissionAmountMarket;

        private BigDecimal platformFeeAmountMarket;

        private BigDecimal vatAmountMarket;

        private BigDecimal platformDeductionRmb;

        private BigDecimal settlementRevenueRmb;

        private BigDecimal purchasePriceRmb;

        private BigDecimal firstLegFeeRmb;

        private BigDecimal warehouseDeliveryFeeRmb;

        private BigDecimal domesticShippingFeeRmb;

        private BigDecimal fulfillmentFeeRmb;

        private BigDecimal totalCostRmb;

        private BigDecimal profitRmb;

        private BigDecimal marginRatePct;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public BigDecimal getGrossRevenueRmb() {
            return grossRevenueRmb;
        }

        public void setGrossRevenueRmb(BigDecimal grossRevenueRmb) {
            this.grossRevenueRmb = grossRevenueRmb;
        }

        public BigDecimal getCommissionRatePct() {
            return commissionRatePct;
        }

        public void setCommissionRatePct(BigDecimal commissionRatePct) {
            this.commissionRatePct = commissionRatePct;
        }

        public BigDecimal getCommissionAmountMarket() {
            return commissionAmountMarket;
        }

        public void setCommissionAmountMarket(BigDecimal commissionAmountMarket) {
            this.commissionAmountMarket = commissionAmountMarket;
        }

        public BigDecimal getPlatformFeeAmountMarket() {
            return platformFeeAmountMarket;
        }

        public void setPlatformFeeAmountMarket(BigDecimal platformFeeAmountMarket) {
            this.platformFeeAmountMarket = platformFeeAmountMarket;
        }

        public BigDecimal getVatAmountMarket() {
            return vatAmountMarket;
        }

        public void setVatAmountMarket(BigDecimal vatAmountMarket) {
            this.vatAmountMarket = vatAmountMarket;
        }

        public BigDecimal getPlatformDeductionRmb() {
            return platformDeductionRmb;
        }

        public void setPlatformDeductionRmb(BigDecimal platformDeductionRmb) {
            this.platformDeductionRmb = platformDeductionRmb;
        }

        public BigDecimal getSettlementRevenueRmb() {
            return settlementRevenueRmb;
        }

        public void setSettlementRevenueRmb(BigDecimal settlementRevenueRmb) {
            this.settlementRevenueRmb = settlementRevenueRmb;
        }

        public BigDecimal getPurchasePriceRmb() {
            return purchasePriceRmb;
        }

        public void setPurchasePriceRmb(BigDecimal purchasePriceRmb) {
            this.purchasePriceRmb = purchasePriceRmb;
        }

        public BigDecimal getFirstLegFeeRmb() {
            return firstLegFeeRmb;
        }

        public void setFirstLegFeeRmb(BigDecimal firstLegFeeRmb) {
            this.firstLegFeeRmb = firstLegFeeRmb;
        }

        public BigDecimal getWarehouseDeliveryFeeRmb() {
            return warehouseDeliveryFeeRmb;
        }

        public void setWarehouseDeliveryFeeRmb(BigDecimal warehouseDeliveryFeeRmb) {
            this.warehouseDeliveryFeeRmb = warehouseDeliveryFeeRmb;
        }

        public BigDecimal getDomesticShippingFeeRmb() {
            return domesticShippingFeeRmb;
        }

        public void setDomesticShippingFeeRmb(BigDecimal domesticShippingFeeRmb) {
            this.domesticShippingFeeRmb = domesticShippingFeeRmb;
        }

        public BigDecimal getFulfillmentFeeRmb() {
            return fulfillmentFeeRmb;
        }

        public void setFulfillmentFeeRmb(BigDecimal fulfillmentFeeRmb) {
            this.fulfillmentFeeRmb = fulfillmentFeeRmb;
        }

        public BigDecimal getTotalCostRmb() {
            return totalCostRmb;
        }

        public void setTotalCostRmb(BigDecimal totalCostRmb) {
            this.totalCostRmb = totalCostRmb;
        }

        public BigDecimal getProfitRmb() {
            return profitRmb;
        }

        public void setProfitRmb(BigDecimal profitRmb) {
            this.profitRmb = profitRmb;
        }

        public BigDecimal getMarginRatePct() {
            return marginRatePct;
        }

        public void setMarginRatePct(BigDecimal marginRatePct) {
            this.marginRatePct = marginRatePct;
        }
    }
}
