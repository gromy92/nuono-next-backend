package com.nuono.next.operationsconfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class OperationCalendarRule {

    private final Long id;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String ruleName;
    private final String activityType;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final String recurringExpression;
    private final String targetScopeType;
    private final String targetScopeValue;
    private final BigDecimal factorValue;
    private final String factorPurpose;
    private final boolean enabled;
    private final Long bundleVersionId;
    private final Long publishRecordId;
    private final OperationConfigPublishStatus publishStatus;
    private final String publishSourceRole;
    private final String publishSourceLabel;
    private final Long createdBy;
    private final Long updatedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public OperationCalendarRule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleName,
            String activityType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String recurringExpression,
            String targetScopeType,
            String targetScopeValue,
            BigDecimal factorValue,
            String factorPurpose,
            boolean enabled,
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
                ruleName,
                activityType,
                dateFrom,
                dateTo,
                recurringExpression,
                targetScopeType,
                targetScopeValue,
                factorValue,
                factorPurpose,
                enabled,
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

    public OperationCalendarRule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleName,
            String activityType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String recurringExpression,
            String targetScopeType,
            String targetScopeValue,
            BigDecimal factorValue,
            String factorPurpose,
            boolean enabled,
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
                ruleName,
                activityType,
                dateFrom,
                dateTo,
                recurringExpression,
                targetScopeType,
                targetScopeValue,
                factorValue,
                factorPurpose,
                enabled,
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

    public OperationCalendarRule(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleName,
            String activityType,
            LocalDate dateFrom,
            LocalDate dateTo,
            String recurringExpression,
            String targetScopeType,
            String targetScopeValue,
            BigDecimal factorValue,
            String factorPurpose,
            boolean enabled,
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
        this.ruleName = ruleName;
        this.activityType = activityType;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.recurringExpression = recurringExpression;
        this.targetScopeType = targetScopeType;
        this.targetScopeValue = targetScopeValue;
        this.factorValue = factorValue;
        this.factorPurpose = factorPurpose;
        this.enabled = enabled;
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

    public OperationCalendarRule withDraftUpdate(
            OperationCalendarRuleDraftCommand command,
            Long nextUpdatedBy,
            LocalDateTime nextUpdatedAt
    ) {
        return new OperationCalendarRule(
                id,
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getRuleName(),
                command.getActivityType(),
                command.getDateFrom(),
                command.getDateTo(),
                command.getRecurringExpression(),
                command.getTargetScopeType(),
                command.getTargetScopeValue(),
                command.getFactorValue(),
                command.getFactorPurpose(),
                command.isEnabled(),
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

    public OperationCalendarRule withPublishRecord(Long nextPublishRecordId) {
        return new OperationCalendarRule(
                id, ownerUserId, storeCode, siteCode, ruleName, activityType, dateFrom, dateTo,
                recurringExpression, targetScopeType, targetScopeValue, factorValue, factorPurpose,
                enabled, nextPublishRecordId, publishStatus, publishSourceRole, publishSourceLabel, createdBy, updatedBy, createdAt, updatedAt,
                bundleVersionId
        );
    }

    public OperationCalendarRule withBundleVersionId(Long nextBundleVersionId) {
        return new OperationCalendarRule(
                id, ownerUserId, storeCode, siteCode, ruleName, activityType, dateFrom, dateTo,
                recurringExpression, targetScopeType, targetScopeValue, factorValue, factorPurpose,
                enabled, publishRecordId, publishStatus, publishSourceRole, publishSourceLabel, createdBy, updatedBy, createdAt, updatedAt,
                nextBundleVersionId
        );
    }

    public OperationCalendarRule withPublishStatus(
            OperationConfigPublishStatus nextStatus,
            Long nextUpdatedBy,
            LocalDateTime nextUpdatedAt
    ) {
        return withPublishStatus(nextStatus, nextUpdatedBy, nextUpdatedAt, publishSourceRole, publishSourceLabel);
    }

    public OperationCalendarRule withPublishStatus(
            OperationConfigPublishStatus nextStatus,
            Long nextUpdatedBy,
            LocalDateTime nextUpdatedAt,
            String nextPublishSourceRole,
            String nextPublishSourceLabel
    ) {
        return new OperationCalendarRule(
                id, ownerUserId, storeCode, siteCode, ruleName, activityType, dateFrom, dateTo,
                recurringExpression, targetScopeType, targetScopeValue, factorValue, factorPurpose,
                enabled, publishRecordId, nextStatus, nextPublishSourceRole, nextPublishSourceLabel,
                createdBy, nextUpdatedBy, createdAt, nextUpdatedAt, bundleVersionId
        );
    }

    public OperationCalendarRule withDisabled(Long nextUpdatedBy, LocalDateTime nextUpdatedAt) {
        return new OperationCalendarRule(
                id, ownerUserId, storeCode, siteCode, ruleName, activityType, dateFrom, dateTo,
                recurringExpression, targetScopeType, targetScopeValue, factorValue, factorPurpose,
                false, publishRecordId, OperationConfigPublishStatus.DISABLED, publishSourceRole, publishSourceLabel,
                createdBy, nextUpdatedBy, createdAt, nextUpdatedAt, bundleVersionId
        );
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getRuleName() { return ruleName; }
    public String getActivityType() { return activityType; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public String getRecurringExpression() { return recurringExpression; }
    public String getTargetScopeType() { return targetScopeType; }
    public String getTargetScopeValue() { return targetScopeValue; }
    public BigDecimal getFactorValue() { return factorValue; }
    public String getFactorPurpose() { return factorPurpose; }
    public boolean isEnabled() { return enabled; }
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
