package com.nuono.next.profit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProfitQuickSignalsView {

    private boolean ready;

    private String message;

    private String signalVersion;

    private String marketCurrency;

    private List<SignalView> signals = new ArrayList<>();

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

    public String getSignalVersion() {
        return signalVersion;
    }

    public void setSignalVersion(String signalVersion) {
        this.signalVersion = signalVersion;
    }

    public String getMarketCurrency() {
        return marketCurrency;
    }

    public void setMarketCurrency(String marketCurrency) {
        this.marketCurrency = marketCurrency;
    }

    public List<SignalView> getSignals() {
        return signals;
    }

    public void setSignals(List<SignalView> signals) {
        this.signals = signals;
    }

    public static class SignalView {

        private String candidateKey;

        private Long candidateId;

        private String title;

        private ProfitQuickSignalStatus status;

        private List<String> missingInputs = new ArrayList<>();

        private List<String> usedDefaults = new ArrayList<>();

        private InputSnapshotView inputSnapshot;

        private List<QuickScenarioView> quickScenarios = new ArrayList<>();

        private DetailSeedView detailSeed;

        public String getCandidateKey() {
            return candidateKey;
        }

        public void setCandidateKey(String candidateKey) {
            this.candidateKey = candidateKey;
        }

        public Long getCandidateId() {
            return candidateId;
        }

        public void setCandidateId(Long candidateId) {
            this.candidateId = candidateId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public ProfitQuickSignalStatus getStatus() {
            return status;
        }

        public void setStatus(ProfitQuickSignalStatus status) {
            this.status = status;
        }

        public List<String> getMissingInputs() {
            return missingInputs;
        }

        public void setMissingInputs(List<String> missingInputs) {
            this.missingInputs = missingInputs;
        }

        public List<String> getUsedDefaults() {
            return usedDefaults;
        }

        public void setUsedDefaults(List<String> usedDefaults) {
            this.usedDefaults = usedDefaults;
        }

        public InputSnapshotView getInputSnapshot() {
            return inputSnapshot;
        }

        public void setInputSnapshot(InputSnapshotView inputSnapshot) {
            this.inputSnapshot = inputSnapshot;
        }

        public List<QuickScenarioView> getQuickScenarios() {
            return quickScenarios;
        }

        public void setQuickScenarios(List<QuickScenarioView> quickScenarios) {
            this.quickScenarios = quickScenarios;
        }

        public DetailSeedView getDetailSeed() {
            return detailSeed;
        }

        public void setDetailSeed(DetailSeedView detailSeed) {
            this.detailSeed = detailSeed;
        }
    }

    public static class InputSnapshotView {

        private String site;

        private BigDecimal salePrice;

        private BigDecimal purchasePrice;

        private BigDecimal lengthCm;

        private BigDecimal widthCm;

        private BigDecimal heightCm;

        private BigDecimal weightGrams;

        public String getSite() {
            return site;
        }

        public void setSite(String site) {
            this.site = site;
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
    }

    public static class QuickScenarioView {

        private String code;

        private String label;

        private BigDecimal profitRmb;

        private BigDecimal marginRatePct;

        private BigDecimal firstLegFeeRmb;

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

        public BigDecimal getFirstLegFeeRmb() {
            return firstLegFeeRmb;
        }

        public void setFirstLegFeeRmb(BigDecimal firstLegFeeRmb) {
            this.firstLegFeeRmb = firstLegFeeRmb;
        }
    }

    public static class DetailSeedView {

        private String title;

        private String site;

        private BigDecimal salePrice;

        private BigDecimal purchasePrice;

        private BigDecimal lengthCm;

        private BigDecimal widthCm;

        private BigDecimal heightCm;

        private BigDecimal weightGrams;

        private BigDecimal vatRate;

        private BigDecimal exchangeRate;

        private BigDecimal domesticShippingFee;

        private BigDecimal warehouseDeliveryUnitPrice;

        private BigDecimal airFreightUnitPrice;

        private BigDecimal oceanFreightUnitPrice;

        private BigDecimal airFreightDimFactor;

        private BigDecimal fbnCommissionRate;

        private BigDecimal fbpCommissionRate;

        private BigDecimal fbnOutboundFee;

        private BigDecimal fbpDirectShipFee;

        private BigDecimal fulfillmentFee;

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

        public BigDecimal getVatRate() {
            return vatRate;
        }

        public void setVatRate(BigDecimal vatRate) {
            this.vatRate = vatRate;
        }

        public BigDecimal getExchangeRate() {
            return exchangeRate;
        }

        public void setExchangeRate(BigDecimal exchangeRate) {
            this.exchangeRate = exchangeRate;
        }

        public BigDecimal getDomesticShippingFee() {
            return domesticShippingFee;
        }

        public void setDomesticShippingFee(BigDecimal domesticShippingFee) {
            this.domesticShippingFee = domesticShippingFee;
        }

        public BigDecimal getWarehouseDeliveryUnitPrice() {
            return warehouseDeliveryUnitPrice;
        }

        public void setWarehouseDeliveryUnitPrice(BigDecimal warehouseDeliveryUnitPrice) {
            this.warehouseDeliveryUnitPrice = warehouseDeliveryUnitPrice;
        }

        public BigDecimal getAirFreightUnitPrice() {
            return airFreightUnitPrice;
        }

        public void setAirFreightUnitPrice(BigDecimal airFreightUnitPrice) {
            this.airFreightUnitPrice = airFreightUnitPrice;
        }

        public BigDecimal getOceanFreightUnitPrice() {
            return oceanFreightUnitPrice;
        }

        public void setOceanFreightUnitPrice(BigDecimal oceanFreightUnitPrice) {
            this.oceanFreightUnitPrice = oceanFreightUnitPrice;
        }

        public BigDecimal getAirFreightDimFactor() {
            return airFreightDimFactor;
        }

        public void setAirFreightDimFactor(BigDecimal airFreightDimFactor) {
            this.airFreightDimFactor = airFreightDimFactor;
        }

        public BigDecimal getFbnCommissionRate() {
            return fbnCommissionRate;
        }

        public void setFbnCommissionRate(BigDecimal fbnCommissionRate) {
            this.fbnCommissionRate = fbnCommissionRate;
        }

        public BigDecimal getFbpCommissionRate() {
            return fbpCommissionRate;
        }

        public void setFbpCommissionRate(BigDecimal fbpCommissionRate) {
            this.fbpCommissionRate = fbpCommissionRate;
        }

        public BigDecimal getFbnOutboundFee() {
            return fbnOutboundFee;
        }

        public void setFbnOutboundFee(BigDecimal fbnOutboundFee) {
            this.fbnOutboundFee = fbnOutboundFee;
        }

        public BigDecimal getFbpDirectShipFee() {
            return fbpDirectShipFee;
        }

        public void setFbpDirectShipFee(BigDecimal fbpDirectShipFee) {
            this.fbpDirectShipFee = fbpDirectShipFee;
        }

        public BigDecimal getFulfillmentFee() {
            return fulfillmentFee;
        }

        public void setFulfillmentFee(BigDecimal fulfillmentFee) {
            this.fulfillmentFee = fulfillmentFee;
        }
    }
}
