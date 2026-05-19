package com.nuono.next.profit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProfitQuickSignalsRequest {

    private String source;

    private Context context = new Context();

    private List<Candidate> candidates = new ArrayList<>();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<Candidate> candidates) {
        this.candidates = candidates;
    }

    public static class Context {

        private String site;

        private BigDecimal salePrice;

        private String titlePrefix;

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

        public String getTitlePrefix() {
            return titlePrefix;
        }

        public void setTitlePrefix(String titlePrefix) {
            this.titlePrefix = titlePrefix;
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

    public static class Candidate {

        private String candidateKey;

        private Long candidateId;

        private String title;

        private BigDecimal purchasePrice;

        private BigDecimal lengthCm;

        private BigDecimal widthCm;

        private BigDecimal heightCm;

        private BigDecimal weightGrams;

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
}
