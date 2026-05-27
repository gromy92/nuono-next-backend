package com.nuono.next.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProductLifecycleCurrentState {

    private final Long id;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String partnerSku;
    private final String sku;
    private final String lifecycleCode;
    private final String lifecycleLabel;
    private final String ruleVersion;
    private final LocalDate analysisDate;
    private final LocalDate listingDate;
    private final String listingDateSource;
    private final String qualityState;
    private final String explanation;
    private final String evidenceJson;
    private final Long lastJobId;
    private final LocalDateTime updatedAt;

    public ProductLifecycleCurrentState(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            String sku,
            String lifecycleCode,
            String lifecycleLabel,
            String ruleVersion,
            LocalDate analysisDate,
            LocalDate listingDate,
            String listingDateSource,
            String qualityState,
            String explanation,
            String evidenceJson,
            Long lastJobId,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.lifecycleCode = lifecycleCode;
        this.lifecycleLabel = lifecycleLabel;
        this.ruleVersion = ruleVersion;
        this.analysisDate = analysisDate;
        this.listingDate = listingDate;
        this.listingDateSource = listingDateSource;
        this.qualityState = qualityState;
        this.explanation = explanation;
        this.evidenceJson = evidenceJson;
        this.lastJobId = lastJobId;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
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

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
    }

    public String getLifecycleCode() {
        return lifecycleCode;
    }

    public String getLifecycleLabel() {
        return lifecycleLabel;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public LocalDate getListingDate() {
        return listingDate;
    }

    public String getListingDateSource() {
        return listingDateSource;
    }

    public String getQualityState() {
        return qualityState;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public Long getLastJobId() {
        return lastJobId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
