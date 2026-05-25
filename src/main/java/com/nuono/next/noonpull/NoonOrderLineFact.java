package com.nuono.next.noonpull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class NoonOrderLineFact {
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String idPartner;
    private final String sourceCountry;
    private final String countryCode;
    private final String destinationCountry;
    private final String bayanNr;
    private final String orderLineIdentity;
    private final String orderIdentity;
    private final String partnerSku;
    private final String sku;
    private final String status;
    private final BigDecimal offerPrice;
    private final BigDecimal gmvLcy;
    private final String currencyCode;
    private final String brandCode;
    private final String family;
    private final String fulfillmentModel;
    private final LocalDateTime orderTimestamp;
    private final LocalDateTime shipmentTimestamp;
    private final LocalDateTime deliveredTimestamp;
    private final LocalDate reportDateFrom;
    private final LocalDate reportDateTo;
    private final String sourceBatchId;

    public NoonOrderLineFact(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String idPartner,
            String sourceCountry,
            String countryCode,
            String destinationCountry,
            String bayanNr,
            String orderLineIdentity,
            String orderIdentity,
            String partnerSku,
            String sku,
            String status,
            BigDecimal offerPrice,
            BigDecimal gmvLcy,
            String currencyCode,
            String brandCode,
            String family,
            String fulfillmentModel,
            LocalDateTime orderTimestamp,
            LocalDateTime shipmentTimestamp,
            LocalDateTime deliveredTimestamp,
            LocalDate reportDateFrom,
            LocalDate reportDateTo,
            String sourceBatchId
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.idPartner = idPartner;
        this.sourceCountry = sourceCountry;
        this.countryCode = countryCode;
        this.destinationCountry = destinationCountry;
        this.bayanNr = bayanNr;
        this.orderLineIdentity = orderLineIdentity;
        this.orderIdentity = orderIdentity;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.status = status;
        this.offerPrice = offerPrice;
        this.gmvLcy = gmvLcy;
        this.currencyCode = currencyCode;
        this.brandCode = brandCode;
        this.family = family;
        this.fulfillmentModel = fulfillmentModel;
        this.orderTimestamp = orderTimestamp;
        this.shipmentTimestamp = shipmentTimestamp;
        this.deliveredTimestamp = deliveredTimestamp;
        this.reportDateFrom = reportDateFrom;
        this.reportDateTo = reportDateTo;
        this.sourceBatchId = sourceBatchId;
    }

    public String naturalKey() {
        return NoonOrderReportDescriptor.SOURCE_SYSTEM + "|" + idPartner + "|" + countryCode + "|" + orderLineIdentity;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getIdPartner() {
        return idPartner;
    }

    public String getSourceCountry() {
        return sourceCountry;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getDestinationCountry() {
        return destinationCountry;
    }

    public String getBayanNr() {
        return bayanNr;
    }

    public String getOrderLineIdentity() {
        return orderLineIdentity;
    }

    public String getOrderIdentity() {
        return orderIdentity;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getOfferPrice() {
        return offerPrice;
    }

    public BigDecimal getGmvLcy() {
        return gmvLcy;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public String getFamily() {
        return family;
    }

    public String getFulfillmentModel() {
        return fulfillmentModel;
    }

    public LocalDateTime getOrderTimestamp() {
        return orderTimestamp;
    }

    public LocalDateTime getShipmentTimestamp() {
        return shipmentTimestamp;
    }

    public LocalDateTime getDeliveredTimestamp() {
        return deliveredTimestamp;
    }

    public LocalDate getReportDateFrom() {
        return reportDateFrom;
    }

    public LocalDate getReportDateTo() {
        return reportDateTo;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }
}
