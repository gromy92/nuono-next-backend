package com.nuono.next.operationsconfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OperationLifecycleRule {

    private final Long id;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String ruleVersion;
    private final String sourceRuleVersion;
    private final Integer newMaxAgeDays;
    private final Integer newMinAgeDays;
    private final BigDecimal highPriceThreshold;
    private final BigDecimal growthMinSalesGrowthRate;
    private final BigDecimal growthMinPvGrowthRate;
    private final BigDecimal growthMinMonthlySales;
    private final Integer growthMinActiveSalesDays;
    private final BigDecimal growthMaxVolatility;
    private final BigDecimal stableMinPvGrowthRate;
    private final BigDecimal stableVolatilityMin;
    private final BigDecimal stableVolatilityMax;
    private final BigDecimal declineMaxVolatility;
    private final BigDecimal declineMaxSalesGrowthRate;
    private final BigDecimal longTailMaxVolatility;
    private final BigDecimal longTailMaxMonthlySales;
    private final Long bundleVersionId;
    private final Long publishRecordId;
    private final OperationConfigPublishStatus publishStatus;
    private final String publishSourceRole;
    private final String publishSourceLabel;
    private final Long createdBy;
    private final Long updatedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public OperationLifecycleRule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleVersion,
            String sourceRuleVersion,
            OperationLifecycleRuleThresholds thresholds,
            Long publishRecordId,
            OperationConfigPublishStatus publishStatus,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                ruleVersion,
                sourceRuleVersion,
                thresholds.getNewMaxAgeDays(),
                thresholds.getNewMinAgeDays(),
                thresholds.getHighPriceThreshold(),
                thresholds.getGrowthMinSalesGrowthRate(),
                thresholds.getGrowthMinPvGrowthRate(),
                thresholds.getGrowthMinMonthlySales(),
                thresholds.getGrowthMinActiveSalesDays(),
                thresholds.getGrowthMaxVolatility(),
                thresholds.getStableMinPvGrowthRate(),
                thresholds.getStableVolatilityMin(),
                thresholds.getStableVolatilityMax(),
                thresholds.getDeclineMaxVolatility(),
                thresholds.getDeclineMaxSalesGrowthRate(),
                thresholds.getLongTailMaxVolatility(),
                thresholds.getLongTailMaxMonthlySales(),
                publishRecordId,
                publishStatus,
                null,
                null,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt,
                null
        );
    }

    public OperationLifecycleRule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleVersion,
            String sourceRuleVersion,
            OperationLifecycleRuleThresholds thresholds,
            Long publishRecordId,
            OperationConfigPublishStatus publishStatus,
            String publishSourceRole,
            String publishSourceLabel,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long bundleVersionId
    ) {
        this(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                ruleVersion,
                sourceRuleVersion,
                thresholds.getNewMaxAgeDays(),
                thresholds.getNewMinAgeDays(),
                thresholds.getHighPriceThreshold(),
                thresholds.getGrowthMinSalesGrowthRate(),
                thresholds.getGrowthMinPvGrowthRate(),
                thresholds.getGrowthMinMonthlySales(),
                thresholds.getGrowthMinActiveSalesDays(),
                thresholds.getGrowthMaxVolatility(),
                thresholds.getStableMinPvGrowthRate(),
                thresholds.getStableVolatilityMin(),
                thresholds.getStableVolatilityMax(),
                thresholds.getDeclineMaxVolatility(),
                thresholds.getDeclineMaxSalesGrowthRate(),
                thresholds.getLongTailMaxVolatility(),
                thresholds.getLongTailMaxMonthlySales(),
                publishRecordId,
                publishStatus,
                publishSourceRole,
                publishSourceLabel,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt,
                bundleVersionId
        );
    }

    public OperationLifecycleRule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleVersion,
            String sourceRuleVersion,
            Integer newMaxAgeDays,
            Integer newMinAgeDays,
            BigDecimal highPriceThreshold,
            BigDecimal growthMinSalesGrowthRate,
            BigDecimal growthMinPvGrowthRate,
            BigDecimal growthMinMonthlySales,
            Integer growthMinActiveSalesDays,
            BigDecimal growthMaxVolatility,
            BigDecimal stableMinPvGrowthRate,
            BigDecimal stableVolatilityMin,
            BigDecimal stableVolatilityMax,
            BigDecimal declineMaxVolatility,
            BigDecimal declineMaxSalesGrowthRate,
            BigDecimal longTailMaxVolatility,
            BigDecimal longTailMaxMonthlySales,
            Long bundleVersionId,
            Long publishRecordId,
            OperationConfigPublishStatus publishStatus,
            String publishSourceRole,
            String publishSourceLabel,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                ruleVersion,
                sourceRuleVersion,
                newMaxAgeDays,
                newMinAgeDays,
                highPriceThreshold,
                growthMinSalesGrowthRate,
                growthMinPvGrowthRate,
                growthMinMonthlySales,
                growthMinActiveSalesDays,
                growthMaxVolatility,
                stableMinPvGrowthRate,
                stableVolatilityMin,
                stableVolatilityMax,
                declineMaxVolatility,
                declineMaxSalesGrowthRate,
                longTailMaxVolatility,
                longTailMaxMonthlySales,
                publishRecordId,
                publishStatus,
                publishSourceRole,
                publishSourceLabel,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt,
                bundleVersionId
        );
    }

    public OperationLifecycleRule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleVersion,
            String sourceRuleVersion,
            OperationLifecycleRuleThresholds thresholds,
            Long publishRecordId,
            OperationConfigPublishStatus publishStatus,
            String publishSourceRole,
            String publishSourceLabel,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                ruleVersion,
                sourceRuleVersion,
                thresholds.getNewMaxAgeDays(),
                thresholds.getNewMinAgeDays(),
                thresholds.getHighPriceThreshold(),
                thresholds.getGrowthMinSalesGrowthRate(),
                thresholds.getGrowthMinPvGrowthRate(),
                thresholds.getGrowthMinMonthlySales(),
                thresholds.getGrowthMinActiveSalesDays(),
                thresholds.getGrowthMaxVolatility(),
                thresholds.getStableMinPvGrowthRate(),
                thresholds.getStableVolatilityMin(),
                thresholds.getStableVolatilityMax(),
                thresholds.getDeclineMaxVolatility(),
                thresholds.getDeclineMaxSalesGrowthRate(),
                thresholds.getLongTailMaxVolatility(),
                thresholds.getLongTailMaxMonthlySales(),
                publishRecordId,
                publishStatus,
                publishSourceRole,
                publishSourceLabel,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt,
                null
        );
    }

    public OperationLifecycleRule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleVersion,
            String sourceRuleVersion,
            Integer newMaxAgeDays,
            Integer newMinAgeDays,
            BigDecimal highPriceThreshold,
            BigDecimal growthMinSalesGrowthRate,
            BigDecimal growthMinPvGrowthRate,
            BigDecimal growthMinMonthlySales,
            Integer growthMinActiveSalesDays,
            BigDecimal growthMaxVolatility,
            BigDecimal stableMinPvGrowthRate,
            BigDecimal stableVolatilityMin,
            BigDecimal stableVolatilityMax,
            BigDecimal declineMaxVolatility,
            BigDecimal declineMaxSalesGrowthRate,
            BigDecimal longTailMaxVolatility,
            BigDecimal longTailMaxMonthlySales,
            Long publishRecordId,
            OperationConfigPublishStatus publishStatus,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                ruleVersion,
                sourceRuleVersion,
                newMaxAgeDays,
                newMinAgeDays,
                highPriceThreshold,
                growthMinSalesGrowthRate,
                growthMinPvGrowthRate,
                growthMinMonthlySales,
                growthMinActiveSalesDays,
                growthMaxVolatility,
                stableMinPvGrowthRate,
                stableVolatilityMin,
                stableVolatilityMax,
                declineMaxVolatility,
                declineMaxSalesGrowthRate,
                longTailMaxVolatility,
                longTailMaxMonthlySales,
                publishRecordId,
                publishStatus,
                null,
                null,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt,
                null
        );
    }

    public OperationLifecycleRule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleVersion,
            String sourceRuleVersion,
            Integer newMaxAgeDays,
            Integer newMinAgeDays,
            BigDecimal highPriceThreshold,
            BigDecimal growthMinSalesGrowthRate,
            BigDecimal growthMinPvGrowthRate,
            BigDecimal growthMinMonthlySales,
            Integer growthMinActiveSalesDays,
            BigDecimal growthMaxVolatility,
            BigDecimal stableMinPvGrowthRate,
            BigDecimal stableVolatilityMin,
            BigDecimal stableVolatilityMax,
            BigDecimal declineMaxVolatility,
            BigDecimal declineMaxSalesGrowthRate,
            BigDecimal longTailMaxVolatility,
            BigDecimal longTailMaxMonthlySales,
            Long publishRecordId,
            OperationConfigPublishStatus publishStatus,
            String publishSourceRole,
            String publishSourceLabel,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                ruleVersion,
                sourceRuleVersion,
                newMaxAgeDays,
                newMinAgeDays,
                highPriceThreshold,
                growthMinSalesGrowthRate,
                growthMinPvGrowthRate,
                growthMinMonthlySales,
                growthMinActiveSalesDays,
                growthMaxVolatility,
                stableMinPvGrowthRate,
                stableVolatilityMin,
                stableVolatilityMax,
                declineMaxVolatility,
                declineMaxSalesGrowthRate,
                longTailMaxVolatility,
                longTailMaxMonthlySales,
                publishRecordId,
                publishStatus,
                publishSourceRole,
                publishSourceLabel,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt,
                null
        );
    }

    public OperationLifecycleRule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleVersion,
            String sourceRuleVersion,
            Integer newMaxAgeDays,
            Integer newMinAgeDays,
            BigDecimal highPriceThreshold,
            BigDecimal growthMinSalesGrowthRate,
            BigDecimal growthMinPvGrowthRate,
            BigDecimal growthMinMonthlySales,
            Integer growthMinActiveSalesDays,
            BigDecimal growthMaxVolatility,
            BigDecimal stableMinPvGrowthRate,
            BigDecimal stableVolatilityMin,
            BigDecimal stableVolatilityMax,
            BigDecimal declineMaxVolatility,
            BigDecimal declineMaxSalesGrowthRate,
            BigDecimal longTailMaxVolatility,
            BigDecimal longTailMaxMonthlySales,
            Long publishRecordId,
            OperationConfigPublishStatus publishStatus,
            String publishSourceRole,
            String publishSourceLabel,
            Long createdBy,
            Long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long bundleVersionId
    ) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.ruleVersion = ruleVersion;
        this.sourceRuleVersion = sourceRuleVersion;
        this.newMaxAgeDays = newMaxAgeDays;
        this.newMinAgeDays = newMinAgeDays;
        this.highPriceThreshold = highPriceThreshold;
        this.growthMinSalesGrowthRate = growthMinSalesGrowthRate;
        this.growthMinPvGrowthRate = growthMinPvGrowthRate;
        this.growthMinMonthlySales = growthMinMonthlySales;
        this.growthMinActiveSalesDays = growthMinActiveSalesDays;
        this.growthMaxVolatility = growthMaxVolatility;
        this.stableMinPvGrowthRate = stableMinPvGrowthRate;
        this.stableVolatilityMin = stableVolatilityMin;
        this.stableVolatilityMax = stableVolatilityMax;
        this.declineMaxVolatility = declineMaxVolatility;
        this.declineMaxSalesGrowthRate = declineMaxSalesGrowthRate;
        this.longTailMaxVolatility = longTailMaxVolatility;
        this.longTailMaxMonthlySales = longTailMaxMonthlySales;
        this.bundleVersionId = bundleVersionId;
        this.publishRecordId = publishRecordId;
        this.publishStatus = publishStatus;
        this.publishSourceRole = OperationConfigVersionSource.safeRole(publishSourceRole);
        this.publishSourceLabel = OperationConfigVersionSource.safeLabel(publishSourceRole, publishSourceLabel);
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public OperationLifecycleRule withDraftUpdate(
            OperationLifecycleRuleDraftCommand command,
            Long nextUpdatedBy,
            LocalDateTime nextUpdatedAt
    ) {
        return new OperationLifecycleRule(
                id,
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                ruleVersion,
                sourceRuleVersion,
                command.getThresholds(),
                publishRecordId,
                publishStatus,
                publishSourceRole,
                publishSourceLabel,
                createdBy,
                nextUpdatedBy,
                createdAt,
                nextUpdatedAt,
                command.getBundleVersionId()
        );
    }

    public OperationLifecycleRule withPublishStatus(
            OperationConfigPublishStatus nextStatus,
            Long nextUpdatedBy,
            LocalDateTime nextUpdatedAt
    ) {
        return withPublishStatus(nextStatus, nextUpdatedBy, nextUpdatedAt, publishSourceRole, publishSourceLabel);
    }

    public OperationLifecycleRule withPublishStatus(
            OperationConfigPublishStatus nextStatus,
            Long nextUpdatedBy,
            LocalDateTime nextUpdatedAt,
            String nextPublishSourceRole,
            String nextPublishSourceLabel
    ) {
        return new OperationLifecycleRule(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                ruleVersion,
                sourceRuleVersion,
                getThresholds(),
                publishRecordId,
                nextStatus,
                nextPublishSourceRole,
                nextPublishSourceLabel,
                createdBy,
                nextUpdatedBy,
                createdAt,
                nextUpdatedAt,
                bundleVersionId
        );
    }

    public OperationLifecycleRuleThresholds getThresholds() {
        return new OperationLifecycleRuleThresholds(
                newMaxAgeDays,
                newMinAgeDays,
                highPriceThreshold,
                growthMinSalesGrowthRate,
                growthMinPvGrowthRate,
                growthMinMonthlySales,
                growthMinActiveSalesDays,
                growthMaxVolatility,
                stableMinPvGrowthRate,
                stableVolatilityMin,
                stableVolatilityMax,
                declineMaxVolatility,
                declineMaxSalesGrowthRate,
                longTailMaxVolatility,
                longTailMaxMonthlySales
        );
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getRuleVersion() { return ruleVersion; }
    public String getSourceRuleVersion() { return sourceRuleVersion; }
    public Integer getNewMaxAgeDays() { return newMaxAgeDays; }
    public Integer getNewMinAgeDays() { return newMinAgeDays; }
    public BigDecimal getHighPriceThreshold() { return highPriceThreshold; }
    public BigDecimal getGrowthMinSalesGrowthRate() { return growthMinSalesGrowthRate; }
    public BigDecimal getGrowthMinPvGrowthRate() { return growthMinPvGrowthRate; }
    public BigDecimal getGrowthMinMonthlySales() { return growthMinMonthlySales; }
    public Integer getGrowthMinActiveSalesDays() { return growthMinActiveSalesDays; }
    public BigDecimal getGrowthMaxVolatility() { return growthMaxVolatility; }
    public BigDecimal getStableMinPvGrowthRate() { return stableMinPvGrowthRate; }
    public BigDecimal getStableVolatilityMin() { return stableVolatilityMin; }
    public BigDecimal getStableVolatilityMax() { return stableVolatilityMax; }
    public BigDecimal getDeclineMaxVolatility() { return declineMaxVolatility; }
    public BigDecimal getDeclineMaxSalesGrowthRate() { return declineMaxSalesGrowthRate; }
    public BigDecimal getLongTailMaxVolatility() { return longTailMaxVolatility; }
    public BigDecimal getLongTailMaxMonthlySales() { return longTailMaxMonthlySales; }
    public Long getBundleVersionId() { return bundleVersionId; }
    public Long getPublishRecordId() { return publishRecordId; }
    public OperationConfigPublishStatus getPublishStatus() { return publishStatus; }
    public String getPublishSourceRole() { return publishSourceRole; }
    public String getPublishSourceLabel() { return publishSourceLabel; }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
