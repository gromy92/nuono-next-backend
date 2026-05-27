package com.nuono.next.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProductLifecycleJobRecord {

    private final Long id;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate anchorDate;
    private final String ruleVersion;
    private final String status;
    private final int processedCount;
    private final int changedCount;
    private final int heldCount;
    private final int dataInsufficientCount;
    private final String failureSummaryJson;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final Long triggeredByUserId;
    private final String triggerSource;

    public ProductLifecycleJobRecord(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate anchorDate,
            String ruleVersion,
            String status,
            int processedCount,
            int changedCount,
            int heldCount,
            int dataInsufficientCount,
            String failureSummaryJson,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        this(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                anchorDate,
                ruleVersion,
                status,
                processedCount,
                changedCount,
                heldCount,
                dataInsufficientCount,
                failureSummaryJson,
                startedAt,
                finishedAt,
                null,
                null
        );
    }

    public ProductLifecycleJobRecord(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate anchorDate,
            String ruleVersion,
            String status,
            int processedCount,
            int changedCount,
            int heldCount,
            int dataInsufficientCount,
            String failureSummaryJson,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Long triggeredByUserId,
            String triggerSource
    ) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.anchorDate = anchorDate;
        this.ruleVersion = ruleVersion;
        this.status = status;
        this.processedCount = processedCount;
        this.changedCount = changedCount;
        this.heldCount = heldCount;
        this.dataInsufficientCount = dataInsufficientCount;
        this.failureSummaryJson = failureSummaryJson;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.triggeredByUserId = triggeredByUserId;
        this.triggerSource = triggerSource;
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

    public LocalDate getAnchorDate() {
        return anchorDate;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public String getStatus() {
        return status;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getChangedCount() {
        return changedCount;
    }

    public int getHeldCount() {
        return heldCount;
    }

    public int getDataInsufficientCount() {
        return dataInsufficientCount;
    }

    public String getFailureSummaryJson() {
        return failureSummaryJson;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public Long getTriggeredByUserId() {
        return triggeredByUserId;
    }

    public String getTriggerSource() {
        return triggerSource;
    }
}
