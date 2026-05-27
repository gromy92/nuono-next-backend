package com.nuono.next.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProductLifecycleHistoryRecord {

    private final Long id;
    private final Long currentStateId;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String partnerSku;
    private final String sku;
    private final String previousLifecycleCode;
    private final String previousLifecycleLabel;
    private final String lifecycleCode;
    private final String lifecycleLabel;
    private final String ruleVersion;
    private final LocalDate analysisDate;
    private final String transitionReason;
    private final String evidenceJson;
    private final LocalDateTime changedAt;

    public ProductLifecycleHistoryRecord(
            Long id,
            Long currentStateId,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String partnerSku,
            String sku,
            String previousLifecycleCode,
            String previousLifecycleLabel,
            String lifecycleCode,
            String lifecycleLabel,
            String ruleVersion,
            LocalDate analysisDate,
            String transitionReason,
            String evidenceJson,
            LocalDateTime changedAt
    ) {
        this.id = id;
        this.currentStateId = currentStateId;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.previousLifecycleCode = previousLifecycleCode;
        this.previousLifecycleLabel = previousLifecycleLabel;
        this.lifecycleCode = lifecycleCode;
        this.lifecycleLabel = lifecycleLabel;
        this.ruleVersion = ruleVersion;
        this.analysisDate = analysisDate;
        this.transitionReason = transitionReason;
        this.evidenceJson = evidenceJson;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getCurrentStateId() {
        return currentStateId;
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

    public String getPreviousLifecycleCode() {
        return previousLifecycleCode;
    }

    public String getPreviousLifecycleLabel() {
        return previousLifecycleLabel;
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

    public String getTransitionReason() {
        return transitionReason;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
